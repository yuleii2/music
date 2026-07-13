package com.k2.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FretboardGeometryTest {
    @Test
    fun sixStringsSpanTheAvailableBoardWidth() {
        val geometry = calculateFretboardGeometry(300f, 420f, 1, 5)
        assertEquals(geometry.left, geometry.stringX(0), 0.001f)
        assertEquals(geometry.right, geometry.stringX(5), 0.001f)
        assertTrue(geometry.fretCenterY(1f) < geometry.fretCenterY(5f))
    }

    @Test
    fun barreDetectionUsesRepeatedFingerAtTheSameFret() {
        val barre = detectPrimaryBarre(
            frets = listOf(1, 3, 3, 2, 1, 1),
            fingers = listOf(1, 3, 4, 2, 1, 1),
        )
        assertNotNull(barre)
        assertEquals(1, barre?.fret)
        assertEquals(0, barre?.firstStringIndex)
        assertEquals(5, barre?.lastStringIndex)
    }
}
