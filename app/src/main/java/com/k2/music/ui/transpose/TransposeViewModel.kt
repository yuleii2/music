package com.k2.music.ui.transpose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.MusicTheoryUtils
import com.k2.music.ui.gateway.CapoSuggestionUi
import com.k2.music.ui.gateway.TransposeGateway
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

enum class TransposeSegment(val label: String) { TRANSPOSE("移调"), CAPO("变调夹") }
enum class CapoMode(val label: String) { FIND_POSITION("寻找品位"), SHAPE_TO_SOUND("计算实际声音") }

data class TransposeUiState(
    val segment: TransposeSegment = TransposeSegment.TRANSPOSE,
    val input: String = "",
    val semitones: Int = 0,
    val preference: MusicTheoryUtils.AccidentalPreference = MusicTheoryUtils.AccidentalPreference.AUTO,
    val result: String = "",
    val error: String? = null,
    val calculating: Boolean = false,
    val capoMode: CapoMode = CapoMode.FIND_POSITION,
    val capoFret: Int = 0,
    val actualChords: String = "",
    val preferredShapes: String = "",
    val shapeInput: String = "",
    val capoResult: String = "",
    val capoSuggestions: List<CapoSuggestionUi> = emptyList(),
    val capoError: String? = null,
)

sealed interface TransposeEffect {
    data class Copy(val text: String) : TransposeEffect
    data class OpenChord(val symbol: String) : TransposeEffect
    data class AddProgression(val progression: String) : TransposeEffect
}

private data class TransposeSnapshot(
    val input: String,
    val semitones: Int,
    val preference: MusicTheoryUtils.AccidentalPreference,
)

private data class CapoSnapshot(
    val mode: CapoMode,
    val capoFret: Int,
    val actual: String,
    val preferred: String,
    val shape: String,
    val preference: MusicTheoryUtils.AccidentalPreference,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TransposeViewModel(
    private val gateway: TransposeGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(readState())
    private val transposeInput = MutableStateFlow(transposeSnapshot())
    private val capoInput = MutableStateFlow(capoSnapshot())
    private val effectsChannel = Channel<TransposeEffect>(Channel.BUFFERED)

    val state: StateFlow<TransposeUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            transposeInput.debounce(100).mapLatest { snapshot ->
                if (snapshot.input.isBlank()) Result.success("")
                else runCatching { gateway.transpose(snapshot.input, snapshot.semitones, snapshot.preference) }
            }.collect { result ->
                result.onSuccess { value ->
                    _state.value = _state.value.copy(result = value, error = null, calculating = false)
                }.onFailure {
                    // Keep a structured error produced by an explicit Calculate action;
                    // background validation must not erase it when both jobs finish together.
                    _state.value = _state.value.copy(
                        result = "",
                        error = _state.value.error,
                        calculating = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            capoInput.debounce(100).mapLatest { snapshot ->
                when (snapshot.mode) {
                    CapoMode.SHAPE_TO_SOUND -> {
                        if (snapshot.shape.isBlank()) Result.success(CapoCalculation(result = ""))
                        else runCatching {
                            CapoCalculation(
                                result = gateway.sounding(snapshot.shape, snapshot.capoFret, snapshot.preference),
                            )
                        }
                    }
                    CapoMode.FIND_POSITION -> {
                        if (snapshot.actual.isBlank() || snapshot.preferred.isBlank()) {
                            Result.success(CapoCalculation())
                        } else runCatching {
                            CapoCalculation(suggestions = gateway.matchingCapos(snapshot.actual, snapshot.preferred))
                        }
                    }
                }
            }.collect { result ->
                result.onSuccess { calculation ->
                    _state.value = _state.value.copy(
                        capoResult = calculation.result,
                        capoSuggestions = calculation.suggestions,
                        capoError = null,
                        calculating = false,
                    )
                }.onFailure {
                    _state.value = _state.value.copy(
                        capoResult = "",
                        capoSuggestions = emptyList(),
                        capoError = _state.value.capoError,
                        calculating = false,
                    )
                }
            }
        }
    }

    fun setSegment(value: TransposeSegment) = update(KEY_SEGMENT, value.name) { copy(segment = value) }
    fun setInput(value: String) = update(KEY_INPUT, value) { copy(input = value, error = null, calculating = value.isNotBlank()) }.also { emitTranspose() }
    fun setSemitones(value: Int) = update(KEY_SEMITONES, value.coerceIn(-11, 11)) { copy(semitones = value.coerceIn(-11, 11), calculating = input.isNotBlank()) }.also { emitTranspose() }
    fun setPreference(value: MusicTheoryUtils.AccidentalPreference) = update(KEY_PREFERENCE, value.name) { copy(preference = value, calculating = input.isNotBlank()) }.also { emitTranspose(); emitCapo() }
    fun setCapoMode(value: CapoMode) = update(KEY_CAPO_MODE, value.name) { copy(capoMode = value, capoError = null) }.also { emitCapo() }
    fun setCapoFret(value: Int) = update(KEY_CAPO_FRET, value.coerceIn(0, 12)) { copy(capoFret = value.coerceIn(0, 12), calculating = shapeInput.isNotBlank()) }.also { emitCapo() }
    fun setActualChords(value: String) = update(KEY_ACTUAL, value) { copy(actualChords = value, capoError = null) }.also { emitCapo() }
    fun setPreferredShapes(value: String) = update(KEY_PREFERRED, value) { copy(preferredShapes = value, capoError = null) }.also { emitCapo() }
    fun setShapeInput(value: String) = update(KEY_SHAPE, value) { copy(shapeInput = value, capoError = null, calculating = value.isNotBlank()) }.also { emitCapo() }

    fun calculateTranspose() {
        viewModelScope.launch {
            val snapshot = transposeSnapshot()
            _state.value = _state.value.copy(calculating = true, error = null)
            runCatching { gateway.transpose(snapshot.input, snapshot.semitones, snapshot.preference) }
                .onSuccess { _state.value = _state.value.copy(result = it, error = null, calculating = false) }
                .onFailure { _state.value = _state.value.copy(result = "", error = it.message ?: "移调失败", calculating = false) }
        }
    }

    fun calculateCapo() {
        viewModelScope.launch {
            val snapshot = capoSnapshot()
            _state.value = _state.value.copy(calculating = true, capoError = null)
            runCatching {
                when (snapshot.mode) {
                    CapoMode.SHAPE_TO_SOUND -> CapoCalculation(
                        result = gateway.sounding(snapshot.shape, snapshot.capoFret, snapshot.preference),
                    )
                    CapoMode.FIND_POSITION -> CapoCalculation(
                        suggestions = gateway.matchingCapos(snapshot.actual, snapshot.preferred),
                    )
                }
            }.onSuccess {
                _state.value = _state.value.copy(
                    capoResult = it.result,
                    capoSuggestions = it.suggestions,
                    capoError = if (snapshot.mode == CapoMode.FIND_POSITION && it.suggestions.isEmpty()) "0–12 品中没有匹配结果。" else null,
                    calculating = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    capoResult = "",
                    capoSuggestions = emptyList(),
                    capoError = it.message ?: "变调夹计算失败",
                    calculating = false,
                )
            }
        }
    }

    fun copyResult() {
        val text = if (_state.value.segment == TransposeSegment.TRANSPOSE) _state.value.result else _state.value.capoResult
        if (text.isNotBlank()) viewModelScope.launch { effectsChannel.send(TransposeEffect.Copy(text)) }
    }

    fun openFirstResult() {
        val source = if (_state.value.segment == TransposeSegment.TRANSPOSE) _state.value.result else _state.value.capoResult
        viewModelScope.launch {
            gateway.splitProgression(source).firstOrNull()?.let { effectsChannel.send(TransposeEffect.OpenChord(it)) }
        }
    }

    fun addResultToProgression() {
        val result = _state.value.result
        if (result.isNotBlank()) viewModelScope.launch { effectsChannel.send(TransposeEffect.AddProgression(result)) }
    }

    private fun emitTranspose() { transposeInput.value = transposeSnapshot() }
    private fun emitCapo() { capoInput.value = capoSnapshot() }

    private fun transposeSnapshot() = TransposeSnapshot(_state.value.input, _state.value.semitones, _state.value.preference)
    private fun capoSnapshot() = CapoSnapshot(
        _state.value.capoMode,
        _state.value.capoFret,
        _state.value.actualChords,
        _state.value.preferredShapes,
        _state.value.shapeInput,
        _state.value.preference,
    )

    private fun <T> update(key: String, value: T, transform: TransposeUiState.() -> TransposeUiState) {
        savedStateHandle[key] = value
        _state.value = _state.value.transform()
    }

    private fun readState() = TransposeUiState(
        segment = enumValue(savedStateHandle[KEY_SEGMENT], TransposeSegment.TRANSPOSE),
        input = savedStateHandle[KEY_INPUT] ?: "",
        semitones = savedStateHandle[KEY_SEMITONES] ?: 0,
        preference = enumValue(savedStateHandle[KEY_PREFERENCE], MusicTheoryUtils.AccidentalPreference.AUTO),
        capoMode = enumValue(savedStateHandle[KEY_CAPO_MODE], CapoMode.FIND_POSITION),
        capoFret = savedStateHandle[KEY_CAPO_FRET] ?: 0,
        actualChords = savedStateHandle[KEY_ACTUAL] ?: "",
        preferredShapes = savedStateHandle[KEY_PREFERRED] ?: "",
        shapeInput = savedStateHandle[KEY_SHAPE] ?: "",
    )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private data class CapoCalculation(
        val result: String = "",
        val suggestions: List<CapoSuggestionUi> = emptyList(),
    )

    private companion object {
        const val KEY_SEGMENT = "transpose_segment"
        const val KEY_INPUT = "transpose_input"
        const val KEY_SEMITONES = "transpose_semitones"
        const val KEY_PREFERENCE = "transpose_preference"
        const val KEY_CAPO_MODE = "capo_mode"
        const val KEY_CAPO_FRET = "capo_fret"
        const val KEY_ACTUAL = "capo_actual"
        const val KEY_PREFERRED = "capo_preferred"
        const val KEY_SHAPE = "capo_shape"
    }
}
