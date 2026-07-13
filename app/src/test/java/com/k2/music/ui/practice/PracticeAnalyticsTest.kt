package com.k2.music.ui.practice

import com.k2.music.PracticeSession
import com.k2.music.TransitionAttempt
import com.k2.music.ui.gateway.DifficultyAction
import com.k2.music.ui.gateway.TransitionKeyUi
import com.k2.music.ui.gateway.calculateTransitionMasteries
import com.k2.music.ui.gateway.suggestPracticeDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeAnalyticsTest {
    @Test
    fun directionalTransitionsStaySeparateAndInsufficientDataHasNoScore() {
        val attempts = listOf(
            attempt("1", "C", "G", true, 1L),
            attempt("2", "G", "C", false, 2L),
        )
        val result = calculateTransitionMasteries(attempts, 10L)
        assertEquals(2, result.size)
        assertEquals(TransitionKeyUi("C", "G"), result[0].key)
        assertEquals(TransitionKeyUi("G", "C"), result[1].key)
        assertNull(result[0].score)
        assertTrue(result[0].reason.contains("数据不足"))
    }

    @Test
    fun failureBreaksStreakAndMasteryIsDeterministic() {
        val attempts = listOf(
            attempt("1", "C", "G", true, 1L),
            attempt("2", "C", "G", true, 2L),
            attempt("3", "C", "G", false, 3L),
            attempt("4", "C", "G", true, 4L),
            attempt("5", "C", "G", true, 5L),
        )
        val first = calculateTransitionMasteries(attempts, 6L).single()
        val second = calculateTransitionMasteries(attempts, 6L).single()
        assertEquals(first, second)
        assertEquals(2, first.currentStreak)
        assertEquals(0.8, first.successRate!!, 0.0001)
    }

    @Test
    fun difficultyRulesRespectSampleAndStableSessionThresholds() {
        val nine = (1..9).map { attempt("$it", "C", "G", true, it.toLong()) }
        assertEquals(DifficultyAction.NEED_MORE_DATA, suggestPracticeDifficulty(nine, 60, 2).action)
        val ten = nine + attempt("10", "C", "G", true, 10L)
        assertEquals(DifficultyAction.KEEP, suggestPracticeDifficulty(ten, 60, 1).action)
        val raise = suggestPracticeDifficulty(ten, 60, 2)
        assertEquals(DifficultyAction.INCREASE_5, raise.action)
        assertEquals(65, raise.suggestedBpm)
    }

    private fun attempt(id: String, from: String, to: String, success: Boolean, time: Long) =
        TransitionAttempt(
            id,
            "session",
            time,
            from,
            to,
            "",
            "",
            60,
            "4/4",
            PracticeSession.SwitchMode.EACH_MEASURE,
            success,
            null,
            PracticeSession.Type.TWO_CHORD_TRANSITION,
        )
}
