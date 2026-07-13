package com.k2.music.ui.recognition

import androidx.lifecycle.SavedStateHandle
import com.k2.music.CustomVoicing
import com.k2.music.ui.FakeChordCatalog
import com.k2.music.ui.FakePlaybackController
import com.k2.music.ui.FakeUserLibrary
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.gateway.CustomVoicingDraft
import com.k2.music.ui.gateway.RecognitionGateway
import com.k2.music.ui.gateway.RecognitionMatchUi
import com.k2.music.ui.testChord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fretChangesUseShortDebounceAndKeepBothInputModes() = runTest(mainDispatcherRule.dispatcher) {
        val chord = testChord("C")
        val match = RecognitionMatchUi(
            symbol = "C",
            chineseName = chord.chineseName,
            score = 100,
            matchLabel = "完全匹配",
            chordNotes = chord.notes,
            actualNotes = chord.notes,
            missingNotes = emptyList(),
            extraNotes = emptyList(),
            inversion = false,
            bassNote = "C",
            chord = chord,
        )
        val gateway = FakeRecognitionGateway(match)
        val viewModel = RecognitionViewModel(
            gateway,
            FakeChordCatalog(listOf(chord)),
            FakePlaybackController(),
            FakeUserLibrary(),
            SavedStateHandle(),
        )
        advanceUntilIdle()
        gateway.fretCalls.clear()

        viewModel.handleFretTap(1, 3)
        advanceTimeBy(60)
        viewModel.handleFretTap(2, 2)
        advanceTimeBy(119)
        assertTrue(gateway.fretCalls.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(1, gateway.fretCalls.size)

        viewModel.updateNotes("C E G")
        viewModel.setInputMode(RecognitionInputMode.NOTES)
        advanceUntilIdle()
        assertEquals("C E G", viewModel.state.value.notes)
        viewModel.setInputMode(RecognitionInputMode.FRETBOARD)
        assertEquals(3, viewModel.state.value.frets[1])
    }

    @Test
    fun clearCanBeUndone() = runTest(mainDispatcherRule.dispatcher) {
        val chord = testChord("C")
        val viewModel = RecognitionViewModel(
            FakeRecognitionGateway(null),
            FakeChordCatalog(listOf(chord)),
            FakePlaybackController(),
            FakeUserLibrary(),
            SavedStateHandle(),
        )
        viewModel.handleFretTap(1, 3)
        viewModel.clear()
        assertEquals(List(6) { UNSET_FRET }, viewModel.state.value.frets)
        viewModel.undoClear()
        assertEquals(3, viewModel.state.value.frets[1])
    }

    @Test
    fun candidateFavoriteUsesSharedUserLibrary() = runTest(mainDispatcherRule.dispatcher) {
        val chord = testChord("C")
        val match = RecognitionMatchUi(
            symbol = "C",
            chineseName = chord.chineseName,
            score = 100,
            matchLabel = "完全匹配",
            chordNotes = chord.notes,
            actualNotes = chord.notes,
            missingNotes = emptyList(),
            extraNotes = emptyList(),
            inversion = false,
            bassNote = "C",
            chord = chord,
        )
        val library = FakeUserLibrary()
        val viewModel = RecognitionViewModel(
            FakeRecognitionGateway(match),
            FakeChordCatalog(listOf(chord)),
            FakePlaybackController(),
            library,
            SavedStateHandle(),
        )
        advanceUntilIdle()

        viewModel.toggleFavorite(match)
        advanceUntilIdle()

        assertTrue("C" in viewModel.state.value.favoriteSymbols)
        assertTrue(library.isFavorite("C"))
    }
}

private class FakeRecognitionGateway(private val match: RecognitionMatchUi?) : RecognitionGateway {
    val fretCalls = mutableListOf<List<Int>>()
    override suspend fun identifyFrets(frets: List<Int>): List<RecognitionMatchUi> {
        fretCalls += frets
        return listOfNotNull(match)
    }
    override suspend fun identifyNotes(notes: String): List<RecognitionMatchUi> = listOfNotNull(match)
    override suspend fun saveCustom(draft: CustomVoicingDraft): CustomVoicing = throw UnsupportedOperationException()
}
