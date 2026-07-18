package com.k2.music.song

import com.k2.music.ChordRepository
import com.k2.music.MusicTheoryUtils
import com.k2.music.PracticePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTransitionExtractorTest {
    private val repository = ChordRepository()
    private val engine = SongArrangementEngine(repository)
    private val extractor = SongTransitionExtractor(RepositorySongChordResolver(repository))

    @Test
    fun directionRepetitionLoopBoundaryAndConsecutiveDuplicatesAreHandledSeparately() {
        val project = project(listOf("C", "C", "G", "C"), repeat = 2)
        val arrangement = engine.arrange(project, PracticePreferences.defaults().withVoicingConstraints(true, 12))

        val all = extractor.extract(project, arrangement, "section", includeLoopBoundary = true)
        val unique = extractor.extract(project, arrangement, "section", includeLoopBoundary = true, unique = true)

        assertFalse(all.any { it.fromChord == "C" && it.toChord == "C" })
        assertTrue(all.count { it.fromChord == "C" && it.toChord == "G" } == 2)
        assertTrue(all.count { it.fromChord == "G" && it.toChord == "C" } == 2)
        assertEquals(listOf(SongTransition("C", "G"), SongTransition("G", "C")), unique)
    }

    @Test
    fun repeatedSectionCreatesBoundaryTransitionAndWholeSongHonorsSectionOrder() {
        val first = section("a", 0, listOf("C", "G"), repeat = 2)
        val second = section("b", 1, listOf("Am", "F"), repeat = 1)
        val project = baseProject(listOf(second, first))
        val arrangement = engine.arrange(project, PracticePreferences.defaults().withVoicingConstraints(true, 12))

        val transitions = extractor.extract(project, arrangement, includeLoopBoundary = false)

        assertEquals(
            listOf(
                SongTransition("C", "G"),
                SongTransition("G", "C"),
                SongTransition("C", "G"),
                SongTransition("G", "Am"),
                SongTransition("Am", "F"),
            ),
            transitions,
        )
    }

    @Test
    fun slashChordAndTransposedNormalizedNamesAreExtractedFromPlayedShapes() {
        val project = engine.configure(
            project(listOf("G/B", "C"), repeat = 1),
            transposeSemitones = 2,
            capoFret = 0,
            accidentalPreference = MusicTheoryUtils.AccidentalPreference.SHARPS,
            now = 20L,
        )
        val arrangement = engine.arrange(project, PracticePreferences.defaults().withVoicingConstraints(true, 12))

        assertEquals(listOf("A/C#", "D"), arrangement.renderedChords.map { it.shapeChord })
        assertEquals(listOf(SongTransition("A/C#", "D")), extractor.extract(project, arrangement))
    }

    private fun project(symbols: List<String>, repeat: Int): SongProject = baseProject(
        listOf(section("section", 0, symbols, repeat)),
    )

    private fun section(id: String, order: Int, symbols: List<String>, repeat: Int): SongSection {
        val events = symbols.mapIndexed { index, symbol ->
            SongChordEvent(
                "$id-event-$index",
                symbol,
                repository.nameParser.parse(symbol).normalizedSymbol,
                null,
                4.0,
                null,
                index,
                index,
            )
        }
        return SongSection(
            id,
            id,
            SongSectionType.CUSTOM,
            order,
            repeat,
            listOf(SongRow("$id-row", "", symbols.joinToString(" "), events, 0)),
        )
    }

    private fun baseProject(sections: List<SongSection>) = SongProject(
        id = "song",
        title = "切换提取",
        artist = "",
        originalText = "source",
        originalKey = "C",
        transposeSemitones = 0,
        capoFret = 0,
        bpm = 60,
        timeSignature = "4/4",
        timingState = SongTimingState.EXPLICIT_BEATS,
        sections = sections,
        notes = "",
        createdAt = 10L,
        updatedAt = 10L,
    )
}
