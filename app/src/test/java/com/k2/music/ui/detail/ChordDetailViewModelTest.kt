package com.k2.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.k2.music.ui.FakeChordCatalog
import com.k2.music.ui.FakePlaybackController
import com.k2.music.ui.FakeUserLibrary
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.testChord
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChordDetailViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun theoreticalChordWithoutVoicingStillLoads() = runTest(mainDispatcherRule.dispatcher) {
        val chord = testChord("Cmaj9", voicings = emptyList())
        val viewModel = ChordDetailViewModel(
            FakeChordCatalog(listOf(chord)),
            FakeUserLibrary(),
            FakePlaybackController(),
            SavedStateHandle(mapOf("symbol" to "Cmaj9")),
        )

        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals("Cmaj9", viewModel.state.value.chord?.symbol)
        assertNull(viewModel.state.value.selectedVoicing)
    }

    @Test
    fun encodedSlashSymbolIsDecodedBeforeLookup() = runTest(mainDispatcherRule.dispatcher) {
        val catalog = FakeChordCatalog(listOf(testChord("G/B")))
        ChordDetailViewModel(
            catalog,
            FakeUserLibrary(),
            FakePlaybackController(),
            SavedStateHandle(mapOf("symbol" to "G%2FB")),
        )
        advanceUntilIdle()
        assertEquals("G/B", catalog.findCalls.first())
    }
}
