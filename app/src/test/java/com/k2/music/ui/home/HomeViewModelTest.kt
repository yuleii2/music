package com.k2.music.ui.home

import androidx.lifecycle.SavedStateHandle
import com.k2.music.ui.FakeChordCatalog
import com.k2.music.ui.FakeUserLibrary
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.testChord
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsRecentAndRecommendations() = runTest(mainDispatcherRule.dispatcher) {
        val catalog = FakeChordCatalog(listOf(testChord("C"), testChord("Am"), testChord("G"), testChord("Fmaj7"), testChord("Dm7"), testChord("G/B")))
        val viewModel = HomeViewModel(catalog, FakeUserLibrary(historySymbols = listOf("Am")), SavedStateHandle())

        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals(listOf("Am"), viewModel.state.value.recent.map { it.symbol })
        assertTrue(viewModel.state.value.recommendations.isNotEmpty())
    }

    @Test
    fun searchUses140MillisecondDebounceAndLatestValue() = runTest(mainDispatcherRule.dispatcher) {
        val catalog = FakeChordCatalog(listOf(testChord("C"), testChord("Cm")))
        val viewModel = HomeViewModel(catalog, FakeUserLibrary(), SavedStateHandle())
        advanceUntilIdle()
        catalog.searchCalls.clear()

        viewModel.updateQuery("C")
        advanceTimeBy(80)
        viewModel.updateQuery("Cm")
        advanceTimeBy(139)
        assertTrue(catalog.searchCalls.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Cm"), catalog.searchCalls)
        assertEquals(listOf("Cm"), viewModel.state.value.searchResults.map { it.symbol })
    }
}
