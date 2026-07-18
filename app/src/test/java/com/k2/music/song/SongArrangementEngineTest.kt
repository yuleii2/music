package com.k2.music.song

import com.k2.music.ChordRepository
import com.k2.music.MusicTheoryUtils
import com.k2.music.PracticePreferences
import com.k2.music.PracticeSession
import com.k2.music.TransitionAttempt
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongArrangementEngineTest {
    private val repository = ChordRepository()
    private val engine = SongArrangementEngine(repository)

    @Test
    fun transposeCapoSlashBassAccidentalPreferenceAndResetAreTheoreticallyConsistent() {
        val source = project(listOf("C", "G/B"), originalKey = "C")
        val configured = engine.configure(
            source,
            transposeSemitones = 2,
            capoFret = 2,
            accidentalPreference = MusicTheoryUtils.AccidentalPreference.SHARPS,
            now = 20L,
        )
        val arrangement = engine.arrange(
            configured,
            PracticePreferences.defaults().withVoicingConstraints(true, 12),
        )

        assertEquals("D", arrangement.soundingKey)
        assertEquals("C", arrangement.shapeKey)
        assertEquals(listOf("D", "A/C#"), arrangement.renderedChords.map { it.soundingChord })
        assertEquals(listOf("C", "G/B"), arrangement.renderedChords.map { it.shapeChord })
        assertEquals(MusicTheoryUtils.AccidentalPreference.SHARPS, configured.accidentalPreference)

        val reset = engine.reset(configured, 30L)
        assertEquals(0, reset.transposeSemitones)
        assertEquals(0, reset.capoFret)
        assertEquals(MusicTheoryUtils.AccidentalPreference.AUTO, reset.accidentalPreference)
        assertEquals(listOf("C", "G/B"), engine.arrange(reset, PracticePreferences.defaults()).renderedChords.map { it.soundingChord })
    }

    @Test
    fun capoPlansAreDeterministicLimitedAndRespectBarreAndMaximumFretFilters() {
        val song = project(listOf("C", "G", "Am", "F"), originalKey = "C")
        val beginner = PracticePreferences.defaults().withVoicingConstraints(false, 5)

        val first = engine.arrange(song, beginner)
        val second = engine.arrange(song, beginner)

        assertEquals(first.capoPlans, second.capoPlans)
        assertTrue(first.capoPlans.size in 1..3)
        assertTrue(first.capoPlans.all { it.barreChordCount == 0 })
        assertTrue(first.capoPlans.all { it.highestFret <= 5 })
        assertTrue(first.renderedChords.mapNotNull { it.voicing }.none { it.barre })
        assertTrue(first.renderedChords.mapNotNull { it.voicing }.all { it.maxFret <= 5 })
    }

    @Test
    fun familiarVoicingAndDirectionalMasteryInfluenceLocalPlanMetadata() {
        val song = project(listOf("C", "G"), originalKey = "C")
        val base = PracticePreferences.defaults().withVoicingConstraints(true, 12)
        val initial = engine.arrange(song, base)
        val chosen = initial.renderedChords.first().availableVoicings.first()
        val familiar = base.withFamiliarVoicing(chosen.id, true)
        val attempts = (0 until 5).map { index ->
            TransitionAttempt(
                "attempt-$index", "session", 100L + index, "C", "G", "", "", 60, "4/4",
                PracticeSession.SwitchMode.EACH_MEASURE, true, 0L,
                PracticeSession.Type.PROGRESSION_LOOP,
            )
        }

        val personalized = engine.arrange(song, familiar, favorites = setOf("C"), attempts = attempts)

        assertTrue(personalized.renderedChords.first().availableVoicings.any { it.id == chosen.id && it.familiar })
        assertTrue(personalized.capoPlans.any { it.averageMastery == 100 })
    }

    @Test
    fun pinnedVoicingPersistsAndMissingChoiceFallsBackWithWarning() {
        val song = project(listOf("C", "G"), originalKey = "C")
        val preferences = PracticePreferences.defaults().withVoicingConstraints(true, 12)
        val initial = engine.arrange(song, preferences)
        val event = initial.renderedChords.first()
        val choice = event.availableVoicings.first()

        val pinned = engine.pinVoicing(song, event.eventId, choice.id, 20L)
        val pinnedArrangement = engine.arrange(pinned, preferences)
        assertTrue(pinnedArrangement.renderedChords.first().voicing?.pinned == true)
        assertEquals(choice.id, pinned.sections.single().rows.single().chordEvents.first().selectedVoicingId)

        val transposed = engine.configure(
            pinned,
            1,
            0,
            MusicTheoryUtils.AccidentalPreference.FLATS,
            30L,
        )
        val fallback = engine.arrange(transposed, preferences)
        assertEquals(choice.id, transposed.sections.single().rows.single().chordEvents.first().selectedVoicingId)
        assertTrue(fallback.renderedChords.first().warning.orEmpty().contains("固定指法"))
        assertFalse(fallback.renderedChords.first().voicing?.pinned == true)

        assertThrows(IllegalArgumentException::class.java) {
            engine.pinVoicing(song, event.eventId, "missing-voicing", 40L)
        }
    }

    private fun project(symbols: List<String>, originalKey: String): SongProject {
        val events = symbols.mapIndexed { index, symbol ->
            val parsed = repository.nameParser.parse(symbol)
            SongChordEvent(
                "event-$index-${UUID.nameUUIDFromBytes(symbol.toByteArray())}",
                symbol,
                parsed.normalizedSymbol,
                index * 4,
                4.0,
                null,
                index,
                index,
            )
        }
        return SongProject(
            id = "song",
            title = "编配测试",
            artist = "",
            originalText = symbols.joinToString(" "),
            originalKey = originalKey,
            transposeSemitones = 0,
            capoFret = 0,
            bpm = 60,
            timeSignature = "4/4",
            timingState = SongTimingState.EXPLICIT_BEATS,
            sections = listOf(
                SongSection(
                    "section", "主歌", SongSectionType.VERSE, 0, 1,
                    listOf(SongRow("row", "", symbols.joinToString(" "), events, 0)),
                ),
            ),
            notes = "",
            createdAt = 10L,
            updatedAt = 10L,
        )
    }
}
