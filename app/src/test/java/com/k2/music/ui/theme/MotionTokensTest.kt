package com.k2.music.ui.theme

import com.k2.music.ui.preferences.MotionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {
    @Test
    fun fullMotionUsesSpecificationDurations() {
        val tokens = motionTokens(MotionLevel.FULL, systemAnimationsEnabled = true)
        assertEquals(90, tokens.instant)
        assertEquals(160, tokens.quick)
        assertEquals(240, tokens.standard)
        assertEquals(360, tokens.emphasized)
        assertEquals(480, tokens.complex)
        assertTrue(tokens.allowSpatialTransitions)
        assertTrue(tokens.allowStagger)
        assertTrue(tokens.allowSprings)
    }

    @Test
    fun reducedMotionKeepsOnlyShortEssentialTransitions() {
        val tokens = motionTokens(MotionLevel.REDUCED, systemAnimationsEnabled = true)
        assertEquals(160, tokens.emphasized)
        assertFalse(tokens.allowSpatialTransitions)
        assertFalse(tokens.allowStagger)
        assertFalse(tokens.allowSprings)
    }

    @Test
    fun disabledSystemAnimationsOverrideTheAppPreference() {
        val tokens = motionTokens(MotionLevel.FULL, systemAnimationsEnabled = false)
        assertEquals(0, tokens.standard)
        assertFalse(tokens.allowSpatialTransitions)
    }

    @Test
    fun disabledAppMotionRemovesEveryCustomTransition() {
        val tokens = motionTokens(MotionLevel.OFF, systemAnimationsEnabled = true)
        assertEquals(0, tokens.instant)
        assertEquals(0, tokens.quick)
        assertEquals(0, tokens.standard)
        assertEquals(0, tokens.emphasized)
        assertEquals(0, tokens.complex)
        assertFalse(tokens.allowSpatialTransitions)
        assertFalse(tokens.allowStagger)
        assertFalse(tokens.allowSprings)
    }
}
