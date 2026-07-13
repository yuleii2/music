package com.k2.music.ui.transpose

import androidx.lifecycle.SavedStateHandle
import com.k2.music.CapoAssistant
import com.k2.music.ChordTransposer
import com.k2.music.MusicTheoryUtils
import com.k2.music.ui.MainDispatcherRule
import com.k2.music.ui.gateway.CapoSuggestionUi
import com.k2.music.ui.gateway.DefaultTransposeGateway
import com.k2.music.ui.gateway.TransposeGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransposeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalidManualCalculationClearsStaleResult() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = TransposeViewModel(FakeTransposeGateway(), SavedStateHandle())
        viewModel.setInput("C G")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.result.isNotBlank())

        viewModel.setInput("H")
        viewModel.calculateTranspose()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.result)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun gatewayKeepsSlashBassInSync() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = DefaultTransposeGateway(ChordTransposer(), CapoAssistant())
        assertEquals(
            "A/C#",
            gateway.transpose("G/B", 2, MusicTheoryUtils.AccidentalPreference.SHARPS),
        )
        assertEquals(
            "D/F#",
            gateway.sounding("C/E", 2, MusicTheoryUtils.AccidentalPreference.SHARPS),
        )
    }
}

private class FakeTransposeGateway : TransposeGateway {
    override suspend fun transpose(
        progression: String,
        semitones: Int,
        preference: MusicTheoryUtils.AccidentalPreference,
    ): String {
        require(progression != "H") { "无法识别和弦：H" }
        return "$progression@$semitones"
    }
    override suspend fun sounding(shape: String, capoFret: Int, preference: MusicTheoryUtils.AccidentalPreference): String = "$shape@$capoFret"
    override suspend fun shapeFor(sounding: String, capoFret: Int, preference: MusicTheoryUtils.AccidentalPreference): String = sounding
    override suspend fun matchingCapos(actual: String, shapes: String): List<CapoSuggestionUi> = emptyList()
    override suspend fun splitProgression(value: String): List<String> = value.split(' ')
}
