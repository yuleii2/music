package com.k2.music.ui.song

import com.k2.music.song.SongTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPracticeStateTest {
    @Test
    fun selectedDifficultiesRoundTripAcrossSavedStateRecreation() {
        val selected = linkedSetOf(
            SongTransition("G/B", "C#maj7"),
            SongTransition("C", "Am"),
        )

        val encoded = encodeSongDifficulties(selected)

        assertEquals(selected, decodeSongDifficulties(encoded))
        assertTrue(decodeSongDifficulties("损坏行\nC\u001FG").contains(SongTransition("C", "G")))
    }
}
