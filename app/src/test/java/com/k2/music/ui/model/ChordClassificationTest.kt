package com.k2.music.ui.model

import com.k2.music.ui.testChord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordClassificationTest {
    @Test
    fun everyBundledQualityBelongsToExactlyOneProductFamily() {
        val qualities = ChordFamily.entries.flatMap { it.qualityIds }

        assertEquals(48, qualities.size)
        assertEquals(48, qualities.toSet().size)
        assertEquals(ChordFamily.SEVENTH, ChordFamily.fromQuality("m7b5"))
        assertEquals(ChordFamily.NINTH, ChordFamily.fromQuality("7#9"))
        assertEquals(ChordFamily.ALTERED, ChordFamily.fromQuality("7#11"))
        assertEquals(ChordFamily.SLASH, testChord("C/E").copy(bassNote = "E").family)
    }

    @Test
    fun flatPreferenceChangesOnlyDisplaySpelling() {
        val chord = testChord("C#").copy(root = "C#")

        assertEquals("C♯", chord.displaySymbol(AccidentalPreference.SHARPS))
        assertEquals("D♭", chord.displaySymbol(AccidentalPreference.FLATS))
        assertEquals("C#", chord.symbol)
        assertTrue(rootChoiceLabel("F#").contains("G♭"))
    }
}
