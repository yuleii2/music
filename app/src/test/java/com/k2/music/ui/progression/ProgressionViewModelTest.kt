package com.k2.music.ui.progression

import androidx.lifecycle.SavedStateHandle
import com.k2.music.VoicingRecommendationMode
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.ProgressionGateway
import com.k2.music.ui.gateway.ProgressionPlaybackUiState
import com.k2.music.ui.gateway.ProgressionTransport
import com.k2.music.ui.gateway.TransportStatus
import com.k2.music.ui.model.ProgressionPlaybackMode
import com.k2.music.ui.model.ProgressionPresetUi
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionSummaryUi
import com.k2.music.ui.model.ProgressionUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun listLoadsSavedItemsAndKeySpecificPresets() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakeProgressionGateway(sampleProgression(saved = true))
        val viewModel = ProgressionListViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals(1, viewModel.state.value.saved.size)
        assertEquals("C", viewModel.state.value.presets.single().keySignature)

        viewModel.setPresetKey("G")
        advanceUntilIdle()
        assertEquals("G", viewModel.state.value.presets.single().keySignature)
    }

    @Test
    fun editorAutoSavesVersionedDraftAfterDebounceAndKeepsReorderButtonsEquivalent() =
        runTest(mainDispatcherRule.dispatcher) {
            val initial = sampleProgression(saved = true)
            val gateway = FakeProgressionGateway(initial)
            val transport = FakeProgressionTransport()
            val viewModel = ProgressionEditorViewModel(
                gateway,
                transport,
                SavedStateHandle(mapOf("id" to initial.id, "seed" to "")),
            )
            advanceUntilIdle()

            viewModel.setName("排练版本")
            advanceTimeBy(649)
            assertEquals(0, gateway.draftSaves)
            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(1, gateway.draftSaves)

            viewModel.moveStep(0, 1)
            assertEquals("G", viewModel.state.value.progression?.steps?.first()?.chordSymbol)
            assertEquals("C", viewModel.state.value.progression?.steps?.get(1)?.chordSymbol)
        }

    @Test
    fun editorUsesSharedTransportInsteadOfUiDelay() = runTest(mainDispatcherRule.dispatcher) {
        val initial = sampleProgression(saved = true)
        val gateway = FakeProgressionGateway(initial)
        val transport = FakeProgressionTransport()
        val viewModel = ProgressionEditorViewModel(
            gateway,
            transport,
            SavedStateHandle(mapOf("id" to initial.id, "seed" to "")),
        )
        advanceUntilIdle()

        viewModel.togglePlayback()
        assertEquals(1, transport.playCalls)
        assertEquals(TransportStatus.PLAYING, transport.state.value.status)

        viewModel.togglePlayback()
        assertEquals(1, transport.toggleCalls)
        assertEquals(TransportStatus.PAUSED, transport.state.value.status)
    }

    @Test
    fun listDeletionKeepsAnUndoPayloadUntilRestored() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakeProgressionGateway(sampleProgression(saved = true))
        val viewModel = ProgressionListViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()

        viewModel.delete(viewModel.state.value.saved.single())
        advanceUntilIdle()
        assertEquals(1, gateway.deleteCalls)

        viewModel.undoDelete()
        advanceUntilIdle()
        assertEquals(1, gateway.restoreCalls)
    }
}

private class FakeProgressionGateway(private var progression: ProgressionUiModel) : ProgressionGateway {
    var draftSaves = 0
    var deleteCalls = 0
    var restoreCalls = 0
    override suspend fun list(): List<ProgressionSummaryUi> = if (progression.saved) listOf(summary()) else emptyList()
    override suspend fun presets(keySignature: String): List<ProgressionPresetUi> = listOf(
        ProgressionPresetUi("pop", "I-V-vi-IV", keySignature, listOf("C", "G", "Am", "F"), 4.0),
    )
    override suspend fun createDraft(seed: String, name: String): ProgressionUiModel = progression.copy(saved = false)
    override suspend fun createPresetDraft(presetId: String, keySignature: String): ProgressionUiModel =
        progression.copy(saved = false, keySignature = keySignature)
    override suspend fun loadEditor(id: String): ProgressionUiModel? = progression.takeIf { it.id == id }
    override suspend fun saveDraft(value: ProgressionUiModel) {
        draftSaves++
        progression = value
    }
    override suspend fun save(value: ProgressionUiModel): ProgressionUiModel = value.copy(saved = true).also { progression = it }
    override suspend fun appendSymbols(value: ProgressionUiModel, symbols: String): ProgressionUiModel = value
    override suspend fun recommend(value: ProgressionUiModel): ProgressionUiModel = value.copy(
        recommendationReasons = listOf("C：测试推荐"),
    )
    override suspend fun duplicate(id: String, name: String): ProgressionSummaryUi = summary().copy(name = name)
    override suspend fun rename(id: String, name: String): ProgressionSummaryUi = summary().copy(name = name)
    override suspend fun delete(id: String): ProgressionUiModel? {
        deleteCalls++
        return progression
    }
    override suspend fun restore(value: ProgressionUiModel): ProgressionSummaryUi {
        restoreCalls++
        progression = value
        return summary()
    }

    private fun summary() = ProgressionSummaryUi(
        progression.id,
        progression.name,
        progression.keySignature,
        progression.bpm,
        progression.timeSignature,
        progression.steps.size,
        progression.symbols,
        progression.updatedAtEpochMillis,
    )
}

private class FakeProgressionTransport : ProgressionTransport {
    private val mutable = MutableStateFlow(ProgressionPlaybackUiState())
    override val state: StateFlow<ProgressionPlaybackUiState> = mutable
    var playCalls = 0
    var toggleCalls = 0
    override fun play(progression: ProgressionUiModel) {
        playCalls++
        mutable.value = ProgressionPlaybackUiState(
            sessionType = PlaybackSessionType.PROGRESSION,
            status = TransportStatus.PLAYING,
            progressionId = progression.id,
            title = progression.name,
        )
    }
    override fun toggle() {
        toggleCalls++
        mutable.value = mutable.value.copy(
            status = if (mutable.value.status == TransportStatus.PLAYING) TransportStatus.PAUSED else TransportStatus.PLAYING,
        )
    }
    override fun pause() { mutable.value = mutable.value.copy(status = TransportStatus.PAUSED) }
    override fun stop() { mutable.value = mutable.value.copy(status = TransportStatus.STOPPED) }
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekToStep(index: Int) { mutable.value = mutable.value.copy(stepIndex = index) }
    override fun updateBpm(value: Int) { mutable.value = mutable.value.copy(bpm = value) }
    override fun updateLoop(value: Boolean) { mutable.value = mutable.value.copy(loop = value) }
    override fun updatePlaybackMode(value: ProgressionPlaybackMode) {
        mutable.value = mutable.value.copy(playbackMode = value)
    }
    override fun startMetronome(bpm: Int, timeSignature: String, accentFirstBeat: Boolean) {
        mutable.value = ProgressionPlaybackUiState(
            sessionType = PlaybackSessionType.METRONOME,
            status = TransportStatus.PLAYING,
            bpm = bpm,
            timeSignature = timeSignature,
        )
    }
    override fun pauseForLifecycle() = pause()
}

private fun sampleProgression(saved: Boolean): ProgressionUiModel = ProgressionUiModel(
    id = "p1",
    name = "测试进行",
    keySignature = "C",
    timeSignature = "4/4",
    bpm = 80,
    loop = true,
    steps = listOf(
        ProgressionStepUi("C", "", 4.0, "", 0, null, emptyList()),
        ProgressionStepUi("G", "", 4.0, "", 1, null, emptyList()),
    ),
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 2,
    notes = "",
    saved = saved,
    recommendationMode = VoicingRecommendationMode.AUTO,
)
