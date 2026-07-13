package com.k2.music.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.ChordCatalogGateway
import com.k2.music.ui.gateway.LibraryFilter
import com.k2.music.ui.gateway.UserLibraryGateway
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.toUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class LibrarySegment(val label: String) {
    ALL("全部"),
    FAVORITES("收藏"),
    RECENT("最近"),
    CUSTOM("自定义"),
}

data class LibraryUiState(
    val loading: Boolean = true,
    val segment: LibrarySegment = LibrarySegment.ALL,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter(),
    val roots: List<String> = emptyList(),
    val qualities: List<Pair<String, String>> = emptyList(),
    val chords: List<ChordUiModel> = emptyList(),
    val selectedSymbols: Set<String> = emptySet(),
    val error: String? = null,
) {
    val selectionMode: Boolean get() = selectedSymbols.isNotEmpty()
}

sealed interface LibraryEffect {
    data class Message(val text: String) : LibraryEffect
    data class FavoritesChanged(
        val symbols: Set<String>,
        val favorite: Boolean,
        val text: String,
    ) : LibraryEffect
}

private data class LibraryRequest(
    val query: String,
    val segment: LibrarySegment,
    val filter: LibraryFilter,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val catalog: ChordCatalogGateway,
    private val userLibrary: UserLibraryGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val queryFlow = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val segmentFlow = savedStateHandle.getStateFlow(KEY_SEGMENT, LibrarySegment.ALL.name)
    private val filterFlow = MutableStateFlow(readFilter())
    private val refreshFlow = MutableStateFlow(0)
    private val _state = MutableStateFlow(
        LibraryUiState(
            query = queryFlow.value,
            segment = segmentValue(segmentFlow.value),
            filter = filterFlow.value,
        ),
    )
    private val effectsChannel = Channel<LibraryEffect>(Channel.BUFFERED)

    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                roots = catalog.roots(),
                qualities = catalog.qualities(),
            )
        }
        viewModelScope.launch {
            combine(
                queryFlow.debounce(140),
                segmentFlow,
                filterFlow,
                refreshFlow,
            ) { query, segmentName, filter, _ ->
                LibraryRequest(query, segmentValue(segmentName), filter)
            }.mapLatest { request ->
                _state.value = _state.value.copy(
                    loading = true,
                    segment = request.segment,
                    query = request.query,
                    filter = request.filter,
                    error = null,
                )
                runCatching { load(request) }
            }.collect { result ->
                result.onSuccess { chords ->
                    _state.value = _state.value.copy(loading = false, chords = chords, error = null)
                }.onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "和弦库暂时无法刷新，请重试。",
                    )
                }
            }
        }
    }

    fun updateQuery(value: String) {
        savedStateHandle[KEY_QUERY] = value
        _state.value = _state.value.copy(query = value)
    }

    fun setSegment(segment: LibrarySegment) {
        savedStateHandle[KEY_SEGMENT] = segment.name
        _state.value = _state.value.copy(segment = segment, selectedSymbols = emptySet())
    }

    fun setFilter(filter: LibraryFilter) {
        savedStateHandle[KEY_ROOT] = filter.root
        savedStateHandle[KEY_QUALITY] = filter.qualityId
        savedStateHandle[KEY_DIFFICULTY] = filter.difficultyBucket
        savedStateHandle[KEY_OPEN] = filter.openOnly
        savedStateHandle[KEY_BARRE] = filter.barreOnly
        savedStateHandle[KEY_SIMPLIFIED] = filter.simplifiedOnly
        filterFlow.value = filter
        _state.value = _state.value.copy(filter = filter)
    }

    fun clearFilters() = setFilter(LibraryFilter())

    fun toggleFavorite(symbol: String) {
        viewModelScope.launch {
            val favorite = userLibrary.toggleFavorite(symbol)
            effectsChannel.send(
                LibraryEffect.FavoritesChanged(
                    symbols = setOf(symbol),
                    favorite = favorite,
                    text = if (favorite) "已加入收藏" else "已取消收藏",
                ),
            )
            refresh()
        }
    }

    fun enterSelection(symbol: String) {
        _state.value = _state.value.copy(selectedSymbols = setOf(symbol))
    }

    fun toggleSelection(symbol: String) {
        val updated = _state.value.selectedSymbols.toMutableSet().apply {
            if (!add(symbol)) remove(symbol)
        }
        _state.value = _state.value.copy(selectedSymbols = updated)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedSymbols = emptySet())
    }

    fun toggleFavoriteSelection() {
        val selected = _state.value.chords.filter { it.symbol in _state.value.selectedSymbols }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val remove = selected.all { userLibrary.isFavorite(it.symbol) }
            val changed = mutableSetOf<String>()
            selected.forEach { chord ->
                val current = userLibrary.isFavorite(chord.symbol)
                if (current != !remove) {
                    userLibrary.toggleFavorite(chord.symbol)
                    changed += chord.symbol
                }
            }
            effectsChannel.send(
                LibraryEffect.FavoritesChanged(
                    symbols = changed,
                    favorite = !remove,
                    text = if (remove) "已取消所选和弦收藏" else "已收藏所选和弦",
                ),
            )
            clearSelection()
            refresh()
        }
    }

    fun undoFavoriteChange(symbols: Set<String>, expectedFavorite: Boolean) {
        viewModelScope.launch {
            symbols.forEach { symbol ->
                if (userLibrary.isFavorite(symbol) == expectedFavorite) {
                    userLibrary.toggleFavorite(symbol)
                }
            }
            effectsChannel.send(LibraryEffect.Message("已撤销收藏更改"))
            refresh()
        }
    }

    fun retry() = refresh()

    private fun refresh() {
        refreshFlow.value += 1
    }

    private suspend fun load(request: LibraryRequest): List<ChordUiModel> {
        val base = catalog.search(request.query, request.filter)
        val favorites = userLibrary.favorites()
        val history = userLibrary.history()
        val custom = userLibrary.customVoicings()
        val customBySymbol = custom.groupBy { it.chordSymbol.lowercase() }
        val decorated = base.associateBy { it.symbol }.mapValues { (_, chord) ->
            chord.copy(
                favorite = chord.symbol in favorites,
                voicings = chord.voicings + customBySymbol[chord.symbol.lowercase()].orEmpty().map {
                    it.toUiModel(chord.symbol)
                },
            )
        }
        return when (request.segment) {
            LibrarySegment.ALL -> base.mapNotNull { decorated[it.symbol] }
            LibrarySegment.FAVORITES -> favorites.mapNotNull { decorated[it] }
            LibrarySegment.RECENT -> history.mapNotNull { decorated[it] }
            LibrarySegment.CUSTOM -> custom.map { it.chordSymbol }.distinct().mapNotNull { symbol ->
                decorated[symbol] ?: catalog.find(symbol).chord?.let { chord ->
                    chord.copy(
                        favorite = symbol in favorites,
                        voicings = chord.voicings + customBySymbol[symbol.lowercase()].orEmpty().map {
                            it.toUiModel(symbol)
                        },
                    )
                }
            }.filter { chord ->
                request.query.isBlank() || base.any { it.symbol == chord.symbol }
            }
        }
    }

    private fun readFilter() = LibraryFilter(
        root = savedStateHandle[KEY_ROOT] ?: "",
        qualityId = savedStateHandle[KEY_QUALITY] ?: "",
        difficultyBucket = savedStateHandle[KEY_DIFFICULTY] ?: 0,
        openOnly = savedStateHandle[KEY_OPEN] ?: false,
        barreOnly = savedStateHandle[KEY_BARRE] ?: false,
        simplifiedOnly = savedStateHandle[KEY_SIMPLIFIED] ?: false,
    )

    private fun segmentValue(raw: String): LibrarySegment =
        LibrarySegment.entries.firstOrNull { it.name == raw } ?: LibrarySegment.ALL

    private companion object {
        const val KEY_QUERY = "library_query"
        const val KEY_SEGMENT = "library_segment"
        const val KEY_ROOT = "library_filter_root"
        const val KEY_QUALITY = "library_filter_quality"
        const val KEY_DIFFICULTY = "library_filter_difficulty"
        const val KEY_OPEN = "library_filter_open"
        const val KEY_BARRE = "library_filter_barre"
        const val KEY_SIMPLIFIED = "library_filter_simplified"
    }
}
