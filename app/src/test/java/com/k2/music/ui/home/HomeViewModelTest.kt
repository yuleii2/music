package com.k2.music.ui.home

import androidx.lifecycle.SavedStateHandle
import com.k2.music.ui.FakeChordCatalog
import com.k2.music.ui.FakeUserLibrary
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.testChord
import com.k2.music.ui.gateway.AttemptProgressUi
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeHomeData
import com.k2.music.ui.gateway.PracticeResultUi
import com.k2.music.ui.gateway.PracticeSummaryUi
import com.k2.music.ui.learning.DailyPracticePlan
import com.k2.music.ui.learning.DailyPracticeTask
import com.k2.music.ui.learning.DailyTaskType
import com.k2.music.ui.learning.LearningProfile
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.ProgressionUiModel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsRecentAndRecommendations() = runTest(mainDispatcherRule.dispatcher) {
        val catalog = FakeChordCatalog(listOf(testChord("C"), testChord("Am"), testChord("G"), testChord("Fmaj7"), testChord("Dm7"), testChord("G/B")))
        val viewModel = HomeViewModel(
            catalog,
            FakeUserLibrary(historySymbols = listOf("Am")),
            HomePracticeGateway(),
            { LearningProfile(onboardingCompleted = true) },
            SavedStateHandle(),
        )

        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals(listOf("Am"), viewModel.state.value.recent.map { it.symbol })
        assertTrue(viewModel.state.value.recommendations.isNotEmpty())
    }

    @Test
    fun searchUses140MillisecondDebounceAndLatestValue() = runTest(mainDispatcherRule.dispatcher) {
        val catalog = FakeChordCatalog(listOf(testChord("C"), testChord("Cm")))
        val viewModel = HomeViewModel(
            catalog,
            FakeUserLibrary(),
            HomePracticeGateway(),
            { LearningProfile(onboardingCompleted = true) },
            SavedStateHandle(),
        )
        advanceUntilIdle()
        catalog.searchCalls.clear()

        viewModel.updateQuery("C")
        advanceTimeBy(80)
        viewModel.updateQuery("Cm")
        advanceTimeBy(139)
        assertTrue(catalog.searchCalls.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Cm"), catalog.searchCalls)
        assertEquals(listOf("Cm"), viewModel.state.value.searchResults.map { it.symbol })
    }
}

private class HomePracticeGateway : PracticeGateway {
    override suspend fun home() = PracticeHomeData(PracticeSummaryUi(), PracticeConfigUi())
    override suspend fun summary() = PracticeSummaryUi()
    override suspend fun dailyPlan(
        profile: LearningProfile,
        favorites: Set<String>,
        availableChords: List<ChordUiModel>,
    ) = DailyPracticePlan(
        0L,
        profile.dailyTargetMinutes,
        availableChords.firstOrNull()?.let {
            listOf(DailyPracticeTask(DailyTaskType.LEARN_NEW_CHORD, "学习 ${it.symbol}", "测试资料", null, it.symbol))
        }.orEmpty(),
        emptyList(),
    )
    override suspend fun prepare(config: PracticeConfigUi): ProgressionUiModel = error("not used")
    override suspend fun savePreferences(config: PracticeConfigUi) = Unit
    override suspend fun sessionProgress(sessionId: String) = AttemptProgressUi()
    override suspend fun recordAttempt(
        sessionId: String,
        config: PracticeConfigUi,
        fromChord: String,
        toChord: String,
        fromVoicingId: String?,
        toVoicingId: String?,
        success: Boolean,
        confirmationOffsetMillis: Long?,
    ) = AttemptProgressUi()
    override suspend fun discardSession(sessionId: String) = Unit
    override suspend fun saveResult(
        sessionId: String,
        startedAtEpochMillis: Long,
        config: PracticeConfigUi,
        actualSeconds: Int,
    ) = PracticeResultUi(
        sessionId, actualSeconds, 0, 0, 0, 0, emptyList(), null, null,
        com.k2.music.ui.gateway.DifficultySuggestionUi(
            com.k2.music.ui.gateway.DifficultyAction.NEED_MORE_DATA, config.bpm, "数据不足",
        ),
    )
}
