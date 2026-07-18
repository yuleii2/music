package com.k2.music.song

import com.k2.music.ChordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSheetParserTest {
    private val parser = SongSheetParser(RepositorySongChordResolver(ChordRepository()))

    @Test
    fun chineseSectionsAndInlineChordsKeepLyricsAndOriginalText() {
        val source = "[主歌]\n[C]今天我走在[G]熟悉的路上\n[Am]想起曾经的[F]时光"
        val result = parser.parse(source)

        assertEquals(source, result.originalText)
        assertEquals(SongSectionType.VERSE, result.sections.single().type)
        assertEquals("今天我走在熟悉的路上", result.sections.single().rows.first().lyricText)
        assertEquals(listOf("C", "G", "Am", "F"), result.validChords)
        assertTrue(result.unrecognizedTokens.isEmpty())
        assertEquals(SongTimingState.UNTYPED, result.timingState)
        assertFalse(result.sections.single().rows.first().chordEvents.first().durationBeats != null)
    }

    @Test
    fun englishAndCustomSectionNamesArePreserved() {
        val source = "Verse 1\nC G\nlyrics\nPre-Chorus\nAm F\nwords\nChorus\nF G\nwords\n[Fingerstyle Part]\nC F"
        val result = parser.parse(source)

        assertEquals(
            listOf(SongSectionType.VERSE, SongSectionType.PRE_CHORUS, SongSectionType.CHORUS, SongSectionType.CUSTOM),
            result.sections.map { it.type },
        )
        assertEquals("Fingerstyle Part", result.sections.last().name)
    }

    @Test
    fun alignedChordLinePairsWithFollowingLyricLine() {
        val result = parser.parse("C              G\n今天我走在熟悉的路上\n\nAm             F\n想起曾经的时光")

        val rows = result.sections.single().rows
        assertEquals(2, rows.size)
        assertEquals("今天我走在熟悉的路上", rows[0].lyricText)
        assertEquals("C              G", rows[0].rawChordText)
        assertEquals(listOf("C", "G"), rows[0].chordEvents.map { it.chordSymbol })
    }

    @Test
    fun measureSyntaxInfersVisibleDurationsForOneTwoAndFourChords() {
        val result = parser.parse("[主歌]\n| C | G |\n| C G | Am F |\n| C G Am F |", "4/4")
        val rows = result.sections.single().rows

        assertEquals(SongTimingState.SIMPLE_MEASURES, result.timingState)
        assertEquals(listOf(4.0, 4.0), rows[0].chordEvents.map { it.durationBeats })
        assertEquals(listOf(2.0, 2.0, 2.0, 2.0), rows[1].chordEvents.map { it.durationBeats })
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), rows[2].chordEvents.map { it.durationBeats })
    }

    @Test
    fun timeSignatureRulesCoverThreeFourSixEightAndExplicitEdits() {
        assertEquals(listOf(3.0), SongTimingRules.inferMeasureDurations(1, "3/4"))
        assertEquals(listOf(1.5, 1.5), SongTimingRules.inferMeasureDurations(2, "3/4"))
        assertEquals(listOf(3.0, 3.0), SongTimingRules.inferMeasureDurations(2, "6/8"))

        val parsed = parser.parse("C G")
        val project = SongProject.create(
            title = "Explicit",
            originalText = parsed.originalText,
            sections = parsed.sections,
            timingState = SongTimingState.UNTYPED,
            now = 10L,
        )
        val ids = project.sections.single().rows.single().chordEvents.map { it.id }
        val first = SongTimingRules.withExplicitDuration(project, ids[0], 2.0, 11L)
        assertEquals(SongTimingState.UNTYPED, first.timingState)
        assertFalse(first.canUsePrecisePlayback)
        val complete = SongTimingRules.withExplicitDuration(first, ids[1], 2.0, 12L)
        assertEquals(SongTimingState.EXPLICIT_BEATS, complete.timingState)
        assertTrue(complete.canUsePrecisePlayback)
    }

    @Test
    fun localCoreValidatesSlashAccidentalsUnicodeAliasesAndExtendedChords() {
        val result = parser.parse("| C | Am | Fmaj7 | C7b9 | Cm7b5 | G/B | F# | Bb | F♯m | B♭ |")

        assertEquals(10, result.chordEventCount)
        assertTrue("Bb" in result.validChords)
        assertTrue(result.unrecognizedTokens.isEmpty())
        assertTrue(result.canStartPractice)
    }

    @Test
    fun englishLyricsContainingChordLettersAreNotMisclassified() {
        val source = "Verse\nA long and winding road\nI see A light and C the sea"
        val result = parser.parse(source)
        val rows = result.sections.single().rows

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.chordEvents.isEmpty() })
        assertEquals(source.lines().drop(1), rows.map { it.lyricText })
        assertTrue(result.validChords.isEmpty())
    }

    @Test
    fun unknownChordLikeTokensAreWarningsInsteadOfBeingDeleted() {
        val source = "[副歌]\n| C | H7 | Aminn | G |"
        val result = parser.parse(source)

        assertEquals(listOf("H7", "Aminn"), result.unrecognizedTokens)
        assertTrue(result.warnings.count { it.code == "UNRECOGNIZED_CHORD" } >= 2)
        assertTrue(result.sections.single().rows.single().rawChordText.contains("H7"))
        assertTrue(result.confidence in 0.0..1.0)
    }

    @Test
    fun suspiciousBracketTokenIsNotSilentlyTurnedIntoCustomHeading() {
        val result = parser.parse("[H7]\n普通文字")

        assertTrue("H7" in result.unrecognizedTokens)
        assertEquals("未分段", result.sections.single().name)
    }

    @Test
    fun parserSupportsManualLineRoleCorrectionAndDeterministicIds() {
        val source = "My Special Section\nC G"
        val corrected = parser.parse(source, lineOverrides = mapOf(1 to SongParseLineRole.SECTION_TITLE))
        val repeated = parser.parse(source, lineOverrides = mapOf(1 to SongParseLineRole.SECTION_TITLE))

        assertEquals("My Special Section", corrected.sections.single().name)
        assertEquals(corrected.sections, repeated.sections)
    }

    @Test
    fun emptyAndTooLongInputsHaveExplicitResults() {
        val empty = parser.parse("  \n")
        assertEquals(0.0, empty.confidence, 0.0)
        assertTrue(empty.warnings.any { it.code == "EMPTY_TEXT" })

        val tooLong = "C".repeat(SongLimits.MAX_ORIGINAL_TEXT_CHARS + 1)
        assertThrows(SongParseException::class.java) { parser.parse(tooLong) }
    }

    @Test
    fun titleAndParserVersionAreReportedWithoutReplacingRawText() {
        val source = "Title: Local Song\n[Intro]\n| C | G |"
        val result = parser.parse(source)

        assertEquals("Local Song", result.detectedTitle)
        assertEquals(SongSheetParser.PARSER_VERSION, result.parserVersion)
        assertEquals(source, result.originalText)
        assertEquals(SongSectionType.INTRO, result.sections.single().type)
    }
}
