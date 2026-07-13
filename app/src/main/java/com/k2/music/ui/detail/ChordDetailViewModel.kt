package com.k2.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.ChordCatalogGateway
import com.k2.music.ui.gateway.ChordPlaybackController
import com.k2.music.ui.gateway.PlaybackUiState
import com.k2.music.ui.gateway.UserLibraryGateway
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.VoicingUiModel
import com.k2.music.ui.model.toUiModel
import com.k2.music.ui.navigation.decodeRouteValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ChordDetailUiState(
    val loading: Boolean = true,
    val chord: ChordUiModel? = null,
    val selectedVoicingIndex: Int = 0,
    val favorite: Boolean = false,
    val familiar: Boolean = false,
    val theoryExpanded: Boolean = false,
    val playback: PlaybackUiState = PlaybackUiState.Idle,
    val infoMessage: String? = null,
    val error: String? = null,
) {
    val selectedVoicing: VoicingUiModel?
        get() = chord?.voicings?.getOrNull(selectedVoicingIndex)
}

sealed interface ChordDetailEffect {
    data class Message(val text: String) : ChordDetailEffect
}

class ChordDetailViewModel(
    private val catalog: ChordCatalogGateway,
    private val userLibrary: UserLibraryGateway,
    private val playbackController: ChordPlaybackController,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val rawSymbol = decodeRouteValue(savedStateHandle.get<String>("symbol").orEmpty())
    private val _state = MutableStateFlow(
        ChordDetailUiState(
            selectedVoicingIndex = savedStateHandle[KEY_VOICING_INDEX] ?: 0,
            theoryExpanded = savedStateHandle[KEY_THEORY_EXPANDED] ?: false,
        ),
    )
    private val effectsChannel = Channel<ChordDetailEffect>(Channel.BUFFERED)

    val state: StateFlow<ChordDetailUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                _state.value = _state.value.copy(playback = playback)
            }
        }
    }

    fun retry() = load()

    fun selectVoicing(index: Int) {
        val chord = _state.value.chord ?: return
        val safe = index.coerceIn(0, (chord.voicings.size - 1).coerceAtLeast(0))
        savedStateHandle[KEY_VOICING_INDEX] = safe
        _state.value = _state.value.copy(selectedVoicingIndex = safe)
        refreshFamiliar()
    }

    fun toggleFavorite() {
        val chord = _state.value.chord ?: return
        viewModelScope.launch {
            val favorite = userLibrary.toggleFavorite(chord.symbol)
            _state.value = _state.value.copy(favorite = favorite, chord = chord.copy(favorite = favorite))
            effectsChannel.send(ChordDetailEffect.Message(if (favorite) "已加入收藏" else "已取消收藏"))
        }
    }

    fun toggleFamiliar() {
        val chord = _state.value.chord ?: return
        val voicing = _state.value.selectedVoicing ?: return
        viewModelScope.launch {
            val familiar = userLibrary.toggleFamiliar(chord, voicing)
            _state.value = _state.value.copy(familiar = familiar)
            effectsChannel.send(ChordDetailEffect.Message(if (familiar) "已标记为熟悉按法" else "已取消熟悉标记"))
        }
    }

    fun play() {
        val chord = _state.value.chord ?: return
        val current = _state.value.playback
        if (current is PlaybackUiState.Playing && current.symbol == chord.symbol) {
            playbackController.stop()
        } else {
            playbackController.play(chord, _state.value.selectedVoicing)
        }
    }

    fun toggleTheory() {
        val expanded = !_state.value.theoryExpanded
        savedStateHandle[KEY_THEORY_EXPANDED] = expanded
        _state.value = _state.value.copy(theoryExpanded = expanded)
    }

    fun applyExperienceMode(showAdvancedTheory: Boolean, showAllVoicings: Boolean) {
        val chord = _state.value.chord
        val selected = if (!showAllVoicings && chord != null) {
            chord.voicings.indexOfFirst { it.recommended }.takeIf { it >= 0 } ?: 0
        } else {
            _state.value.selectedVoicingIndex
        }
        savedStateHandle[KEY_THEORY_EXPANDED] = showAdvancedTheory
        savedStateHandle[KEY_VOICING_INDEX] = selected
        _state.value = _state.value.copy(
            theoryExpanded = showAdvancedTheory,
            selectedVoicingIndex = selected,
        )
        refreshFamiliar()
    }

    fun deleteSelectedCustomVoicing() {
        val id = _state.value.selectedVoicing?.customId ?: return
        viewModelScope.launch {
            if (userLibrary.deleteCustomVoicing(id)) {
                effectsChannel.send(ChordDetailEffect.Message("已删除自定义指法；内置指法未受影响"))
                savedStateHandle[KEY_VOICING_INDEX] = 0
                load()
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val lookup = catalog.find(rawSymbol)
            if (!lookup.recognized || lookup.chord == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = lookup.message ?: "无法加载该和弦。",
                )
                return@launch
            }
            val custom = userLibrary.customVoicings(lookup.chord.symbol)
            val favorite = userLibrary.isFavorite(lookup.chord.symbol)
            val chord = lookup.chord.copy(
                favorite = favorite,
                voicings = lookup.chord.voicings + custom.map { it.toUiModel(lookup.chord.symbol) },
            )
            val selected = (_state.value.selectedVoicingIndex).coerceIn(
                0,
                (chord.voicings.size - 1).coerceAtLeast(0),
            )
            userLibrary.addHistory(chord.symbol)
            val familiar = chord.voicings.getOrNull(selected)?.let { userLibrary.isFamiliar(chord, it) } ?: false
            _state.value = _state.value.copy(
                loading = false,
                chord = chord,
                selectedVoicingIndex = selected,
                favorite = favorite,
                familiar = familiar,
                infoMessage = lookup.message,
                error = null,
            )
        }
    }

    private fun refreshFamiliar() {
        val chord = _state.value.chord ?: return
        val voicing = _state.value.selectedVoicing ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(familiar = userLibrary.isFamiliar(chord, voicing))
        }
    }

    private companion object {
        const val KEY_VOICING_INDEX = "detail_voicing_index"
        const val KEY_THEORY_EXPANDED = "detail_theory_expanded"
    }
}
