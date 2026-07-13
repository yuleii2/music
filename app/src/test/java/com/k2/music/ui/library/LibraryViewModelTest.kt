package com.k2.music.ui.library

import androidx.lifecycle.SavedStateHandle
import com.k2.music.ui.FakeChordCatalog
import com.k2.music.ui.FakeUserLibrary
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.testChord
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun restoresFavoriteSegmentAndOnlyShowsFavorites() = runTest(mainDispatcherRule.dispatcher) {
        val handle = SavedStateHandle(mapOf("library_segment" to LibrarySegment.FAVORITES.name))
        val viewModel = LibraryViewModel(
            FakeChordCatalog(listOf(testChord("C"), testChord("Am"))),
            FakeUserLibrary(favoriteSymbols = listOf("Am")),
            handle,
        )

        advanceUntilIdle()

        assertEquals(LibrarySegment.FAVORITES, viewModel.state.value.segment)
        assertEquals(listOf("Am"), viewModel.state.value.chords.map { it.symbol })
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun selectionCanBeExitedWithoutLeavingTheScreen() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = LibraryViewModel(
            FakeChordCatalog(listOf(testChord("C"))),
            FakeUserLibrary(),
            SavedStateHandle(),
        )
        advanceUntilIdle()
        viewModel.enterSelection("C")
        assertEquals(setOf("C"), viewModel.state.value.selectedSymbols)
        viewModel.clearSelection()
        assertEquals(emptySet<String>(), viewModel.state.value.selectedSymbols)
    }

    @Test
    fun favoriteChangeCanBeUndoneWithoutOverwritingLaterState() = runTest(mainDispatcherRule.dispatcher) {
        val library = FakeUserLibrary()
        val viewModel = LibraryViewModel(
            FakeChordCatalog(listOf(testChord("C"))),
            library,
            SavedStateHandle(),
        )
        advanceUntilIdle()

        viewModel.toggleFavorite("C")
        advanceUntilIdle()
        assertTrue(library.isFavorite("C"))

        viewModel.undoFavoriteChange(setOf("C"), expectedFavorite = true)
        advanceUntilIdle()
        assertFalse(library.isFavorite("C"))
    }
}
