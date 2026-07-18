package com.k2.music.ui.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperienceCapabilitiesTest {
    @Test
    fun beginnerAndProfessionalModesChangeRealDefaults() {
        val beginner = ExperienceMode.BEGINNER.capabilities()
        val professional = ExperienceMode.PROFESSIONAL.capabilities()
        assertFalse(beginner.showAdvancedTheoryByDefault)
        assertFalse(beginner.showAllVoicingsByDefault)
        assertFalse(beginner.defaultAllowBarre)
        assertEquals(50, beginner.defaultPracticeBpm)
        assertFalse(beginner.showSongTheoryFields)
        assertFalse(beginner.expandAdvancedSongRhythmEditor)
        assertEquals(1, beginner.songCapoPlanLimit)
        assertEquals(6, beginner.songVoicingPreviewLimit)
        assertTrue(professional.showAdvancedTheoryByDefault)
        assertTrue(professional.showAllVoicingsByDefault)
        assertTrue(professional.defaultAllowBarre)
        assertTrue(professional.expandAdvancedPracticeSettings)
        assertTrue(professional.showSongTheoryFields)
        assertTrue(professional.expandAdvancedSongRhythmEditor)
        assertEquals(3, professional.songCapoPlanLimit)
    }
}
