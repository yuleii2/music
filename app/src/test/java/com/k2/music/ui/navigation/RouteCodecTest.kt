package com.k2.music.ui.navigation

import com.k2.music.ui.gateway.ExportScopeUi
import com.k2.music.ui.gateway.PracticeConfigUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RouteCodecTest {
    @Test
    fun slashChordDoesNotBecomeAPathSegment() {
        val route = chordDetailRoute("G/B")
        assertEquals("chord-detail?symbol=G%2FB", route)
        assertFalse(route.substringAfter("symbol=").contains('/'))
        assertEquals("G/B", decodeRouteValue(route.substringAfter("symbol=")))
    }

    @Test
    fun accidentalAndSlashRoundTrip() {
        listOf("D/F#", "C#maj7", "B♭m7", "C△7").forEach { symbol ->
            assertEquals(symbol, decodeRouteValue(encodeRouteValue(symbol)))
        }
    }

    @Test
    fun progressionSeedKeepsSpacesAndSlashChords() {
        val route = progressionEditorRoute("C G/B Am F")
        assertEquals("C G/B Am F", decodeRouteValue(route.substringAfter("seed=")))
    }

    @Test
    fun progressionIdIsKeptSeparateFromSeed() {
        val route = progressionEditorByIdRoute("draft/id#1")
        assertEquals("draft/id#1", decodeRouteValue(route.substringAfter("id=").substringBefore('&')))
        assertEquals("", route.substringAfter("seed="))
    }

    @Test
    fun practiceAndExportRoutesEncodeSlashChords() {
        val practice = practiceSessionRoute(
            PracticeConfigUi(
                symbols = "G/B D/F#",
                sourceProgressionId = "saved/progression#1",
                useProgressionRhythm = true,
            ),
        )
        val practiceSymbols = practice.substringAfter("symbols=").substringBefore('&')
        assertEquals("G/B D/F#", decodeRouteValue(practiceSymbols))
        assertFalse(practiceSymbols.contains('/'))
        assertEquals(
            "saved/progression#1",
            decodeRouteValue(practice.substringAfter("progressionId=").substringBefore('&')),
        )
        assertEquals("true", practice.substringAfter("progressionRhythm="))

        val export = exportRoute(ExportScopeUi.SELECTION, listOf("G/B", "D/F#"))
        val exportSymbols = export.substringAfter("symbols=").substringBefore('&')
        assertEquals("G/B\nD/F#", decodeRouteValue(exportSymbols))
        assertFalse(exportSymbols.contains('/'))
    }
}
