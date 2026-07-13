package com.k2.music.ui.recognition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.ChordCatalogGateway
import com.k2.music.ui.gateway.ChordPlaybackController
import com.k2.music.ui.gateway.CustomVoicingDraft
import com.k2.music.ui.gateway.RecognitionGateway
import com.k2.music.ui.gateway.RecognitionMatchUi
import com.k2.music.ui.gateway.UserLibraryGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val UNSET_FRET = -2

enum class RecognitionInputMode(val label: String) { FRETBOARD("交互指板"), NOTES("音符输入") }
enum class FretInputTool(val label: String) { FRET("品位"), OPEN("空弦"), MUTED("闷弦"), ERASE("擦除") }

data class RecognitionUiState(
    val inputMode: RecognitionInputMode = RecognitionInputMode.FRETBOARD,
    val inputTool: FretInputTool = FretInputTool.FRET,
    val frets: List<Int> = List(6) { UNSET_FRET },
    val startFret: Int = 1,
    val notes: String = "",
    val calculating: Boolean = false,
    val matches: List<RecognitionMatchUi> = emptyList(),
    val favoriteSymbols: Set<String> = emptySet(),
    val error: String? = null,
)

sealed interface RecognitionEffect {
    data object Cleared : RecognitionEffect
    data class Message(val text: String) : RecognitionEffect
    data class Saved(val symbol: String) : RecognitionEffect
    data class FavoriteChanged(val symbol: String, val favorite: Boolean) : RecognitionEffect
}

private data class RecognitionSnapshot(
    val mode: RecognitionInputMode,
    val frets: List<Int>,
    val notes: String,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecognitionViewModel(
    private val recognition: RecognitionGateway,
    private val catalog: ChordCatalogGateway,
    private val playback: ChordPlaybackController,
    private val userLibrary: UserLibraryGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredFrets = savedStateHandle.get<IntArray>(KEY_FRETS)?.toList()?.takeIf { it.size == 6 }
        ?: List(6) { UNSET_FRET }
    private val _state = MutableStateFlow(
        RecognitionUiState(
            inputMode = enumValue(savedStateHandle[KEY_MODE], RecognitionInputMode.FRETBOARD),
            inputTool = enumValue(savedStateHandle[KEY_TOOL], FretInputTool.FRET),
            frets = restoredFrets,
            startFret = savedStateHandle[KEY_START_FRET] ?: 1,
            notes = savedStateHandle[KEY_NOTES] ?: "",
        ),
    )
    private val input = MutableStateFlow(snapshot())
    private val effectsChannel = Channel<RecognitionEffect>(Channel.BUFFERED)
    private var clearedFrets: List<Int>? = null

    val state: StateFlow<RecognitionUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching { userLibrary.favorites().toSet() }.onSuccess {
                _state.value = _state.value.copy(favoriteSymbols = it)
            }
        }
        viewModelScope.launch {
            input.debounce(120).mapLatest { snapshot ->
                runCatching { identify(snapshot) }
            }.collect { result ->
                result.onSuccess { matches ->
                    _state.value = _state.value.copy(calculating = false, matches = matches, error = null)
                }.onFailure {
                    _state.value = _state.value.copy(calculating = false, matches = emptyList(), error = null)
                }
            }
        }
    }

    fun setInputMode(mode: RecognitionInputMode) {
        savedStateHandle[KEY_MODE] = mode.name
        _state.value = _state.value.copy(inputMode = mode, error = null)
        emitInput()
    }

    fun setInputTool(tool: FretInputTool) {
        savedStateHandle[KEY_TOOL] = tool.name
        _state.value = _state.value.copy(inputTool = tool)
    }

    fun setStartFret(value: Int) {
        val safe = value.coerceIn(1, 26)
        savedStateHandle[KEY_START_FRET] = safe
        _state.value = _state.value.copy(startFret = safe)
    }

    fun handleFretTap(stringIndex: Int, absoluteFret: Int) {
        if (stringIndex !in 0..5) return
        val current = _state.value.frets[stringIndex]
        val value = when (_state.value.inputTool) {
            FretInputTool.FRET -> if (current == absoluteFret) UNSET_FRET else absoluteFret.coerceIn(1, 30)
            FretInputTool.OPEN -> if (current == 0) UNSET_FRET else 0
            FretInputTool.MUTED -> if (current == -1) UNSET_FRET else -1
            FretInputTool.ERASE -> UNSET_FRET
        }
        updateFret(stringIndex, value)
    }

    fun cycleString(stringIndex: Int) {
        if (stringIndex !in 0..5) return
        val next = when (_state.value.frets[stringIndex]) {
            UNSET_FRET -> 0
            0 -> -1
            -1 -> _state.value.startFret
            else -> UNSET_FRET
        }
        updateFret(stringIndex, next)
    }

    fun clear() {
        clearedFrets = _state.value.frets
        val empty = List(6) { UNSET_FRET }
        saveFrets(empty)
        _state.value = _state.value.copy(frets = empty, matches = emptyList(), error = null)
        emitInput()
        viewModelScope.launch { effectsChannel.send(RecognitionEffect.Cleared) }
    }

    fun undoClear() {
        val previous = clearedFrets ?: return
        clearedFrets = null
        saveFrets(previous)
        _state.value = _state.value.copy(frets = previous)
        emitInput()
    }

    fun updateNotes(value: String) {
        savedStateHandle[KEY_NOTES] = value
        _state.value = _state.value.copy(notes = value, error = null)
        emitInput()
    }

    fun identifyNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(calculating = true, error = null)
            runCatching { identify(snapshot()) }
                .onSuccess { matches ->
                    _state.value = _state.value.copy(
                        calculating = false,
                        matches = matches,
                        error = if (matches.isEmpty()) "没有可靠候选，请增加更多实际发声音。" else null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        calculating = false,
                        matches = emptyList(),
                        error = error.message ?: "无法识别当前输入。",
                    )
                }
        }
    }

    fun playCandidate(match: RecognitionMatchUi) {
        viewModelScope.launch {
            val chord = catalog.find(match.symbol).chord ?: match.chord
            playback.play(chord, chord.previewVoicing)
        }
    }

    fun toggleFavorite(match: RecognitionMatchUi) {
        viewModelScope.launch {
            runCatching { userLibrary.toggleFavorite(match.symbol) }
                .onSuccess { favorite ->
                    val favorites = _state.value.favoriteSymbols.toMutableSet().apply {
                        if (favorite) add(match.symbol) else remove(match.symbol)
                    }
                    _state.value = _state.value.copy(favoriteSymbols = favorites)
                    effectsChannel.send(RecognitionEffect.FavoriteChanged(match.symbol, favorite))
                }
                .onFailure {
                    effectsChannel.send(RecognitionEffect.Message(it.message ?: "收藏状态更新失败。"))
                }
        }
    }

    fun saveCustom(
        match: RecognitionMatchUi,
        chordSymbol: String,
        name: String,
        fingersText: String,
        startFret: Int,
        note: String,
    ) {
        viewModelScope.launch {
            runCatching {
                val fingers = parseFingers(fingersText)
                recognition.saveCustom(
                    CustomVoicingDraft(
                        chordSymbol = chordSymbol.ifBlank { match.symbol },
                        name = name.ifBlank { "${match.symbol} 自定义指法" },
                        frets = _state.value.frets,
                        fingers = fingers,
                        startFret = startFret,
                        note = note,
                    ),
                )
            }.onSuccess {
                effectsChannel.send(RecognitionEffect.Saved(it.chordSymbol))
            }.onFailure {
                effectsChannel.send(RecognitionEffect.Message(it.message ?: "自定义指法保存失败。"))
            }
        }
    }

    private fun updateFret(index: Int, value: Int) {
        val updated = _state.value.frets.toMutableList().apply { this[index] = value }
        saveFrets(updated)
        _state.value = _state.value.copy(frets = updated, error = null, calculating = true)
        emitInput()
    }

    private fun saveFrets(value: List<Int>) {
        savedStateHandle[KEY_FRETS] = value.toIntArray()
    }

    private fun emitInput() {
        input.value = snapshot()
    }

    private fun snapshot() = RecognitionSnapshot(_state.value.inputMode, _state.value.frets, _state.value.notes)

    private suspend fun identify(snapshot: RecognitionSnapshot): List<RecognitionMatchUi> =
        when (snapshot.mode) {
            RecognitionInputMode.FRETBOARD -> recognition.identifyFrets(snapshot.frets)
            RecognitionInputMode.NOTES -> recognition.identifyNotes(snapshot.notes)
        }

    private fun parseFingers(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        val values = raw.trim().split(Regex("[,，\\s]+"))
        require(values.size == 6) { "手指编号需填写六个 0–4 数字，或全部留空" }
        return values.map {
            val value = it.toIntOrNull() ?: throw IllegalArgumentException("手指编号必须是 0–4")
            require(value in 0..4) { "手指编号必须是 0–4" }
            value
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_MODE = "recognition_mode"
        const val KEY_TOOL = "recognition_tool"
        const val KEY_FRETS = "recognition_frets"
        const val KEY_START_FRET = "recognition_start_fret"
        const val KEY_NOTES = "recognition_notes"
    }
}
