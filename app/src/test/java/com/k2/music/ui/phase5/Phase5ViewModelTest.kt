package com.k2.music.ui.phase5

import androidx.lifecycle.SavedStateHandle
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.ai.AiAssistantViewModel
import com.k2.music.ui.ai.AiSettingsViewModel
import com.k2.music.ui.export.ExportViewModel
import com.k2.music.ui.gateway.AiAcceptResult
import com.k2.music.ui.gateway.AiGateway
import com.k2.music.ui.gateway.AiResultUi
import com.k2.music.ui.gateway.AiSettingsUi
import com.k2.music.ui.gateway.AiTaskUi
import com.k2.music.ui.gateway.AttemptProgressUi
import com.k2.music.ui.gateway.ExportFormatUi
import com.k2.music.ui.gateway.ExportGateway
import com.k2.music.ui.gateway.ExportProgressUi
import com.k2.music.ui.gateway.ExportRequestUi
import com.k2.music.ui.gateway.ExportScopeUi
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeHomeData
import com.k2.music.ui.gateway.PracticeResultUi
import com.k2.music.ui.gateway.PracticeSummaryUi
import com.k2.music.ui.gateway.ProgressionPlaybackUiState
import com.k2.music.ui.gateway.ProgressionTransport
import com.k2.music.ui.gateway.TransportStatus
import com.k2.music.ui.model.ProgressionPlaybackMode
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionUiModel
import com.k2.music.ui.practice.PracticeElapsedClock
import com.k2.music.ui.practice.PracticeSessionViewModel
import com.k2.music.ui.practice.PracticeSetupViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase5ViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun practiceSessionUsesAbsoluteElapsedTimeAndRestoresPausedState() = runTest(mainDispatcherRule.dispatcher) {
        val handle = practiceHandle()
        val gateway = FakePracticeGateway()
        val transport = FakeTransport()
        val clock = MutablePracticeClock(1_000L)
        val first = PracticeSessionViewModel(gateway, transport, handle, clock)
        runCurrent()
        assertFalse(first.state.value.loading)
        assertEquals(TransportStatus.PLAYING, transport.state.value.status)

        clock.now = 21_000L
        advanceTimeBy(100)
        runCurrent()
        assertEquals(40_000L, first.state.value.remainingMillis)
        first.pause()

        val restored = PracticeSessionViewModel(gateway, FakeTransport(), handle, clock)
        runCurrent()
        assertTrue(restored.state.value.paused)
        assertEquals(40_000L, restored.state.value.remainingMillis)
    }

    @Test
    fun setupReportsLocalValidationErrorBeforeNavigation() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakePracticeGateway(prepareError = "双和弦模式需要两个和弦")
        val viewModel = PracticeSetupViewModel(gateway, practiceHandle(symbols = "C"))
        viewModel.start()
        advanceUntilIdle()
        assertEquals("双和弦模式需要两个和弦", viewModel.state.value.error)
    }

    @Test
    fun practiceSuccessAndFailureProduceDifferentTrustedCounts() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakePracticeGateway()
        val transport = FakeTransport()
        val viewModel = PracticeSessionViewModel(
            gateway,
            transport,
            practiceHandle(),
            MutablePracticeClock(1_000L),
        )
        runCurrent()

        viewModel.recordSuccess()
        runCurrent()
        assertEquals(1, viewModel.state.value.completionCount)
        assertEquals(1, viewModel.state.value.successCount)
        assertEquals(1, viewModel.state.value.currentStreak)

        transport.advanceTo(index = 1, anchorNanos = 2_000_000_000L)
        viewModel.recordFailure()
        runCurrent()
        assertEquals(2, viewModel.state.value.completionCount)
        assertEquals(1, viewModel.state.value.failureCount)
        assertEquals(0, viewModel.state.value.currentStreak)
        assertEquals(1, viewModel.state.value.bestStreak)

        viewModel.pause()
        viewModel.recordSuccess()
        runCurrent()
        assertEquals(2, viewModel.state.value.completionCount)
    }

    @Test
    fun samePlaybackStepCanOnlyBeRecordedOnceAndNextAnchorUnlocksRecording() =
        runTest(mainDispatcherRule.dispatcher) {
            val gateway = FakePracticeGateway()
            val transport = FakeTransport()
            val viewModel = PracticeSessionViewModel(
                gateway,
                transport,
                practiceHandle(),
                MutablePracticeClock(1_000L),
            )
            runCurrent()

            viewModel.recordSuccess()
            runCurrent()
            viewModel.recordFailure()
            runCurrent()
            assertEquals(1, gateway.currentProgress.attemptCount)
            assertEquals(1, gateway.currentProgress.successCount)

            transport.advanceTo(index = 1, anchorNanos = 2_000_000_000L)
            viewModel.recordFailure()
            runCurrent()
            assertEquals(2, gateway.currentProgress.attemptCount)
            assertEquals(1, gateway.currentProgress.failureCount)
            viewModel.pause()
        }

    @Test
    fun selectedSongDirectionRecordsOnlyTheRequestedTransition() = runTest(mainDispatcherRule.dispatcher) {
        val handle = practiceHandle().apply {
            this["songId"] = "song"
            this["songSectionId"] = "chorus"
            this["songFrom"] = "C"
            this["songTo"] = "G"
        }
        val gateway = FakePracticeGateway()
        val transport = FakeTransport()
        val viewModel = PracticeSessionViewModel(gateway, transport, handle, MutablePracticeClock(1_000L))
        runCurrent()

        viewModel.recordFailure()
        runCurrent()
        assertEquals(0, gateway.currentProgress.attemptCount)
        assertTrue(viewModel.state.value.error.orEmpty().contains("G → C"))

        transport.advanceTo(index = 1, anchorNanos = 2_000_000_000L)
        viewModel.recordSuccess()
        runCurrent()
        assertEquals(1, gateway.currentProgress.attemptCount)
        assertEquals(1, gateway.currentProgress.successCount)
        viewModel.pause()
    }

    @Test
    fun finishWaitsForPendingAttemptBeforeSavingSummary() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakePracticeGateway(recordGate = gate)
        val viewModel = PracticeSessionViewModel(
            gateway,
            FakeTransport(),
            practiceHandle(),
            MutablePracticeClock(1_000L),
        )
        runCurrent()

        viewModel.recordSuccess()
        runCurrent()
        viewModel.finish()
        runCurrent()
        assertEquals(-1, gateway.savedResultAttemptCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, gateway.savedResultAttemptCount)
    }

    @Test
    fun resetAndAbandonLeaveNoPendingAttempts() = runTest(mainDispatcherRule.dispatcher) {
        val resetGate = CompletableDeferred<Unit>()
        val resetGateway = FakePracticeGateway(recordGate = resetGate)
        val resetViewModel = PracticeSessionViewModel(
            resetGateway,
            FakeTransport(),
            practiceHandle(),
            MutablePracticeClock(1_000L),
        )
        runCurrent()
        resetViewModel.recordSuccess()
        runCurrent()
        resetViewModel.reset()
        resetGate.complete(Unit)
        runCurrent()
        assertEquals(0, resetGateway.currentProgress.attemptCount)
        assertEquals(0, resetViewModel.state.value.completionCount)
        resetViewModel.pause()

        val abandonGate = CompletableDeferred<Unit>()
        val abandonGateway = FakePracticeGateway(recordGate = abandonGate)
        val abandonViewModel = PracticeSessionViewModel(
            abandonGateway,
            FakeTransport(),
            practiceHandle(),
            MutablePracticeClock(1_000L),
        )
        runCurrent()
        val effect = async { abandonViewModel.effects.first() }
        abandonViewModel.recordFailure()
        runCurrent()
        abandonViewModel.abandon()
        abandonGate.complete(Unit)
        runCurrent()
        assertTrue(effect.await() is com.k2.music.ui.practice.PracticeSessionEffect.Abandoned)
        assertEquals(0, abandonGateway.currentProgress.attemptCount)
    }

    @Test
    fun aiUiStateNeverContainsFullApiKeyAndCancellationReachesGateway() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakeAiGateway()
        val settings = AiSettingsViewModel(gateway)
        settings.save("super-secret-api-key")
        advanceUntilIdle()
        assertFalse(settings.state.value.toString().contains("super-secret-api-key"))
        assertTrue(settings.state.value.settings.hasApiKey)

        val assistant = AiAssistantViewModel(gateway, SavedStateHandle())
        assistant.setInput("warm jazz")
        assistant.cancel()
        assertTrue(gateway.cancelled)
        assertNull(AiSettingsUiStateApiKeyFieldProbe.findSecretField())
    }

    @Test
    fun exportProgressKeepsSuccessFailureAndFirstFile() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakeExportGateway()
        val handle = SavedStateHandle(
            mapOf(
                "scope" to ExportScopeUi.FAVORITES.name,
                "symbols" to "",
                "index" to 0,
                "export_folder" to "content://test/tree",
            ),
        )
        val viewModel = ExportViewModel(gateway, handle)
        advanceUntilIdle()
        viewModel.start()
        advanceUntilIdle()
        val progress = requireNotNull(viewModel.state.value.progress)
        assertFalse(progress.running)
        assertEquals(2, progress.succeeded)
        assertEquals(1, progress.failed)
        assertEquals("C-1.jpg", progress.firstFileName)
    }
}

private object AiSettingsUiStateApiKeyFieldProbe {
    fun findSecretField(): String? =
        com.k2.music.ui.ai.AiSettingsUiState::class.java.declaredFields
            .firstOrNull { it.name.contains("apiKey", ignoreCase = true) }
            ?.name
}

private class MutablePracticeClock(var now: Long) : PracticeElapsedClock {
    override fun elapsedRealtimeMillis(): Long = now
}

private class FakePracticeGateway(
    private val prepareError: String? = null,
    private val recordGate: CompletableDeferred<Unit>? = null,
) : PracticeGateway {
    private var progress = AttemptProgressUi()
    val currentProgress: AttemptProgressUi get() = progress
    var savedResultAttemptCount: Int = -1
    override suspend fun home() = PracticeHomeData(PracticeSummaryUi(), PracticeConfigUi())
    override suspend fun summary() = PracticeSummaryUi()
    override suspend fun dailyPlan(
        profile: com.k2.music.ui.learning.LearningProfile,
        favorites: Set<String>,
        availableChords: List<com.k2.music.ui.model.ChordUiModel>,
    ) = com.k2.music.ui.learning.DailyPracticePlan(0L, profile.dailyTargetMinutes, emptyList(), emptyList())
    override suspend fun prepare(config: PracticeConfigUi): ProgressionUiModel {
        prepareError?.let { throw IllegalArgumentException(it) }
        return practiceProgression(config)
    }
    override suspend fun savePreferences(config: PracticeConfigUi) = Unit
    override suspend fun sessionProgress(sessionId: String): AttemptProgressUi = progress
    override suspend fun recordAttempt(
        sessionId: String,
        config: PracticeConfigUi,
        fromChord: String,
        toChord: String,
        fromVoicingId: String?,
        toVoicingId: String?,
        success: Boolean,
        confirmationOffsetMillis: Long?,
        stepToken: String,
    ): AttemptProgressUi {
        recordGate?.await()
        val streak = if (success) progress.currentStreak + 1 else 0
        progress = progress.copy(
            attemptCount = progress.attemptCount + 1,
            successCount = progress.successCount + if (success) 1 else 0,
            failureCount = progress.failureCount + if (success) 0 else 1,
            currentStreak = streak,
            bestStreak = maxOf(progress.bestStreak, streak),
        )
        return progress
    }
    override suspend fun discardSession(sessionId: String) { progress = AttemptProgressUi() }
    override suspend fun saveResult(
        sessionId: String,
        startedAtEpochMillis: Long,
        config: PracticeConfigUi,
        actualSeconds: Int,
    ): PracticeResultUi {
        savedResultAttemptCount = progress.attemptCount
        return PracticeResultUi(
        "result",
        actualSeconds,
        progress.attemptCount,
        progress.successCount,
        progress.failureCount,
        progress.bestStreak,
        listOf("C", "G"),
        null,
        null,
        com.k2.music.ui.gateway.DifficultySuggestionUi(
            com.k2.music.ui.gateway.DifficultyAction.NEED_MORE_DATA,
            config.bpm,
            "数据不足",
        ),
        )
    }
}

private class FakeTransport : ProgressionTransport {
    private val mutable = MutableStateFlow(ProgressionPlaybackUiState())
    override val state: StateFlow<ProgressionPlaybackUiState> = mutable
    override fun play(progression: ProgressionUiModel) {
        mutable.value = ProgressionPlaybackUiState(
            sessionType = PlaybackSessionType.PROGRESSION,
            status = TransportStatus.PLAYING,
            progressionId = progression.id,
            title = progression.name,
            stepIndex = 0,
            currentSymbol = progression.steps.first().chordSymbol,
            nextSymbol = progression.steps.getOrNull(1)?.chordSymbol.orEmpty(),
            stepAnchorNanos = 1_000_000_000L,
        )
    }
    override fun toggle() {
        mutable.value = mutable.value.copy(
            status = if (mutable.value.status == TransportStatus.PLAYING) TransportStatus.PAUSED else TransportStatus.PLAYING,
        )
    }
    override fun pause() { mutable.value = mutable.value.copy(status = TransportStatus.PAUSED) }
    override fun stop() { mutable.value = mutable.value.copy(status = TransportStatus.STOPPED) }
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekToStep(index: Int) = Unit
    override fun updateBpm(value: Int) = Unit
    override fun updateLoop(value: Boolean) = Unit
    override fun updatePlaybackMode(value: ProgressionPlaybackMode) = Unit
    override fun startMetronome(bpm: Int, timeSignature: String, accentFirstBeat: Boolean) = Unit
    override fun pauseForLifecycle() = pause()

    fun advanceTo(index: Int, anchorNanos: Long) {
        mutable.value = mutable.value.copy(stepIndex = index, stepAnchorNanos = anchorNanos)
    }
}

private class FakeAiGateway : AiGateway {
    private var stored = AiSettingsUi(hasApiKey = false)
    var cancelled = false
    override fun settings(): AiSettingsUi = stored
    override fun isConfigured(): Boolean = stored.enabled && stored.hasApiKey
    override fun saveSettings(settings: AiSettingsUi, newApiKey: String?) {
        stored = settings.copy(hasApiKey = stored.hasApiKey || !newApiKey.isNullOrBlank())
    }
    override fun clearSettings() { stored = AiSettingsUi() }
    override fun clearCache() = Unit
    override suspend fun testConnection(): String = "连接成功"
    override suspend fun submit(task: AiTaskUi, input: String, contextSymbol: String): AiResultUi =
        AiResultUi(task = task, title = "result", aiExplanation = "ai", localValidation = "local")
    override fun cancel() { cancelled = true }
    override fun accept(result: AiResultUi): AiAcceptResult = AiAcceptResult.None
}

private class FakeExportGateway : ExportGateway {
    override suspend fun count(request: ExportRequestUi): Int = 3
    override suspend fun export(
        request: ExportRequestUi,
        folderUri: String,
        prefix: String,
        format: ExportFormatUi,
        onProgress: (ExportProgressUi) -> Unit,
    ): ExportProgressUi {
        onProgress(ExportProgressUi(3, 1, 1, 0, "C-1.jpg"))
        return ExportProgressUi(3, 3, 2, 1, "C-1.jpg", running = false)
    }
}

private fun practiceHandle(symbols: String = "C G") = SavedStateHandle(
    mapOf(
        "mode" to "TWO_CHORD",
        "symbols" to symbols,
        "duration" to 60,
        "bpm" to 80,
        "signature" to "4/4",
        "switch" to "EACH_MEASURE",
        "accent" to true,
        "barre" to true,
        "maxFret" to 12,
    ),
)
private fun practiceProgression(config: PracticeConfigUi) = ProgressionUiModel(
    id = "practice",
    name = "练习",
    keySignature = "C",
    timeSignature = config.timeSignature,
    bpm = config.bpm,
    loop = true,
    steps = listOf(
        ProgressionStepUi("C", "", 4.0, "", 0, null, emptyList()),
        ProgressionStepUi("G", "", 4.0, "", 1, null, emptyList()),
    ),
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
    notes = "",
    saved = false,
)
