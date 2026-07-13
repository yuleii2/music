package com.k2.music.ui.learning

import com.k2.music.PracticeSession
import com.k2.music.TransitionAttempt
import com.k2.music.ui.gateway.PracticeModeUi
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.VoicingUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPracticePlannerTest {
    private val planner = DefaultDailyPracticePlanner()
    private val chords = listOf("C", "G", "Am", "Em", "D", "A", "Dm", "E").map(::chord)

    @Test
    fun newUserGetsRealBeginnerContentWithoutFakeWeakestTransition() {
        val plan = planner.createPlan(LearningProfile(onboardingCompleted = true), emptyList(), emptyList(), emptySet(), emptySet(), chords, 100L)
        assertEquals("第一次换和弦：C 与 G", plan.tasks.first().title)
        assertNull(plan.weakestTransition)
        assertEquals("C", plan.newContent?.chordSymbol)
    }

    @Test
    fun trustedHistoryCreatesDirectionalWeakReviewAndDirectContinueConfig() {
        val session = PracticeSession.recorded(
            "s", 1L, 100L, PracticeSession.Type.TWO_CHORD_TRANSITION, listOf("C", "G"), 60,
            "4/4", PracticeSession.SwitchMode.EACH_MEASURE, 60, 60, 6, 2, 4, 1,
        )
        val attempts = (0 until 6).map { index ->
            TransitionAttempt(
                "a$index", "s", index.toLong(), "C", "G", "", "", 60, "4/4",
                PracticeSession.SwitchMode.EACH_MEASURE, index < 2, null,
                PracticeSession.Type.TWO_CHORD_TRANSITION,
            )
        }
        val plan = planner.createPlan(LearningProfile(onboardingCompleted = true), attempts, listOf(session), emptySet(), emptySet(), chords, 200L)
        assertNotNull(plan.weakestTransition)
        assertTrue(plan.weakestTransition!!.title.contains("C → G"))
        assertEquals(PracticeModeUi.TWO_CHORD, plan.tasks.first { it.type == DailyTaskType.CONTINUE_LAST }.config!!.mode)
        assertFalse(plan.weakestTransition!!.title.contains("G → C"))
    }

    private fun chord(symbol: String) = ChordUiModel(
        symbol, symbol, symbol.take(1), "major", "大三和弦", "", listOf("1", "3", "5"),
        listOf("C", "E", "G"), emptyList(), "", listOf(
            VoicingUiModel("$symbol|open", null, "开放按法", listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0), 1, 5, "入门", true, false, false, "", emptyList(), emptyList()),
        ),
    )
}
