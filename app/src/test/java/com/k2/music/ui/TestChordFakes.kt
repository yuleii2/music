package com.k2.music.ui

import com.k2.music.CustomVoicing
import com.k2.music.ui.gateway.ChordCatalogGateway
import com.k2.music.ui.gateway.ChordPlaybackController
import com.k2.music.ui.gateway.LibraryFilter
import com.k2.music.ui.gateway.PlaybackUiState
import com.k2.music.ui.gateway.UserLibraryGateway
import com.k2.music.ui.model.ChordLookupUiResult
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.VoicingUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun testChord(
    symbol: String,
    voicings: List<VoicingUiModel> = listOf(testVoicing("$symbol-v1")),
): ChordUiModel = ChordUiModel(
    symbol = symbol,
    chineseName = "$symbol 测试和弦",
    root = symbol.take(1),
    qualityId = "maj",
    quality = "大三和弦",
    bassNote = symbol.substringAfter('/', ""),
    intervals = listOf("1", "3", "5"),
    notes = listOf("C", "E", "G"),
    aliases = emptyList(),
    description = "测试说明",
    voicings = voicings,
)

fun testVoicing(id: String): VoicingUiModel = VoicingUiModel(
    id = id,
    name = "开放按法",
    frets = listOf(-1, 3, 2, 0, 1, 0),
    fingers = listOf(0, 3, 2, 0, 1, 0),
    startFret = 1,
    displayFrets = 4,
    difficulty = "入门",
    recommended = true,
    simplified = false,
    barre = false,
    description = "",
    stringNotes = listOf(null, "C", "E", "G", "C", "E"),
    midiNotes = listOf(0, 48, 52, 55, 60, 64),
)

class FakeChordCatalog(
    chords: List<ChordUiModel>,
) : ChordCatalogGateway {
    private val bySymbol = chords.associateBy { it.symbol }
    val searchCalls = mutableListOf<String>()
    val findCalls = mutableListOf<String>()

    override suspend fun allChords(): List<ChordUiModel> = bySymbol.values.toList()

    override suspend fun search(query: String, filter: LibraryFilter): List<ChordUiModel> {
        searchCalls += query
        return bySymbol.values.filter { chord ->
            query.isBlank() || chord.symbol.contains(query, ignoreCase = true)
        }
    }

    override suspend fun find(rawSymbol: String): ChordLookupUiResult {
        findCalls += rawSymbol
        val chord = bySymbol[rawSymbol]
        return if (chord != null) ChordLookupUiResult(true, chord) else ChordLookupUiResult(false, message = "not found")
    }

    override suspend fun examples(): List<String> = bySymbol.keys.toList()
    override suspend fun roots(): List<String> = bySymbol.values.map { it.root }.distinct()
    override suspend fun qualities(): List<Pair<String, String>> = listOf("maj" to "大三和弦")
    override fun usesFallbackData(): Boolean = false
    override fun dataLoadMessage(): String = "ok"
}

class FakeUserLibrary(
    favoriteSymbols: List<String> = emptyList(),
    historySymbols: List<String> = emptyList(),
) : UserLibraryGateway {
    private val favorite = favoriteSymbols.toMutableList()
    private val recent = historySymbols.toMutableList()
    override suspend fun favorites(): List<String> = favorite.toList()
    override suspend fun history(): List<String> = recent.toList()
    override suspend fun customVoicings(): List<CustomVoicing> = emptyList()
    override suspend fun customVoicings(symbol: String): List<CustomVoicing> = emptyList()
    override suspend fun isFavorite(symbol: String): Boolean = symbol in favorite
    override suspend fun toggleFavorite(symbol: String): Boolean {
        if (!favorite.remove(symbol)) favorite.add(0, symbol)
        return symbol in favorite
    }
    override suspend fun addHistory(symbol: String) {
        recent.remove(symbol)
        recent.add(0, symbol)
    }
    override suspend fun removeHistory(symbol: String): Boolean = recent.remove(symbol)
    override suspend fun deleteCustomVoicing(id: String): Boolean = false
    override suspend fun isFamiliar(chord: ChordUiModel, voicing: VoicingUiModel): Boolean = false
    override suspend fun toggleFamiliar(chord: ChordUiModel, voicing: VoicingUiModel): Boolean = true
}

class FakePlaybackController : ChordPlaybackController {
    private val mutableState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
    override val state: StateFlow<PlaybackUiState> = mutableState
    override fun play(chord: ChordUiModel, voicing: VoicingUiModel?, arpeggio: Boolean) {
        mutableState.value = PlaybackUiState.Playing(chord.symbol, voicing?.name ?: "组成音")
    }
    override fun stop() {
        mutableState.value = PlaybackUiState.Idle
    }
}
