package com.k2.music.ui.progression

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.VoicingRecommendationMode
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.ProgressionGateway
import com.k2.music.ui.gateway.ProgressionTransport
import com.k2.music.ui.gateway.TransportStatus
import com.k2.music.ui.model.ProgressionPlaybackMode
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ProgressionEditorUiState(
    val loading: Boolean = true,
    val progression: ProgressionUiModel? = null,
    val selectedStepIndex: Int = 0,
    val addInput: String = "",
    val dirty: Boolean = false,
    val savingDraft: Boolean = false,
    val error: String? = null,
)

sealed interface ProgressionEditorEffect {
    data class Message(val text: String) : ProgressionEditorEffect
    data object NavigateBack : ProgressionEditorEffect
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ProgressionEditorViewModel(
    private val gateway: ProgressionGateway,
    private val transport: ProgressionTransport,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressionEditorUiState())
    private val draftChanges = MutableSharedFlow<ProgressionUiModel>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val effectsChannel = Channel<ProgressionEditorEffect>(Channel.BUFFERED)

    val state: StateFlow<ProgressionEditorUiState> = _state.asStateFlow()
    val playback = transport.state
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            draftChanges.debounce(650).mapLatest { value ->
                _state.value = _state.value.copy(savingDraft = true)
                runCatching { gateway.saveDraft(value) }
            }.collect { result ->
                _state.value = _state.value.copy(savingDraft = false)
                result.onFailure {
                    effectsChannel.send(
                        ProgressionEditorEffect.Message(it.message ?: "草稿自动保存失败。"),
                    )
                }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val id = savedStateHandle.get<String>("id").orEmpty()
            val seed = savedStateHandle.get<String>("seed").orEmpty()
            runCatching {
                (if (id.isNotBlank()) gateway.loadEditor(id) else null)
                    ?: gateway.createDraft(seed).also { gateway.saveDraft(it) }
            }.onSuccess { value ->
                _state.value = ProgressionEditorUiState(loading = false, progression = value)
                if (value.restoredDraft) {
                    effectsChannel.send(ProgressionEditorEffect.Message("已恢复上次未保存草稿"))
                }
            }.onFailure {
                _state.value = ProgressionEditorUiState(
                    loading = false,
                    error = it.message ?: "无法打开和弦进行。",
                )
            }
        }
    }

    fun setName(value: String) = mutate { copy(name = value) }
    fun setKey(value: String) = mutate { copy(keySignature = value) }
    fun setBpm(value: Int) {
        mutate { copy(bpm = value.coerceIn(40, 240)) }
        if (transport.state.value.progressionId == _state.value.progression?.id) {
            transport.updateBpm(value)
        }
    }
    fun setTimeSignature(value: String) = mutate { copy(timeSignature = value) }
    fun setLoop(value: Boolean) {
        mutate { copy(loop = value) }
        if (transport.state.value.progressionId == _state.value.progression?.id) transport.updateLoop(value)
    }
    fun setPlaybackMode(value: ProgressionPlaybackMode) {
        mutate { copy(playbackMode = value) }
        if (transport.state.value.progressionId == _state.value.progression?.id) {
            transport.updatePlaybackMode(value)
        }
    }
    fun setRecommendationMode(value: VoicingRecommendationMode) = mutate { copy(recommendationMode = value) }
    fun setAllowBarre(value: Boolean) = mutate { copy(allowBarre = value) }
    fun setMaxFret(value: Int) = mutate { copy(maxFret = value.coerceIn(1, 24)) }

    fun setAddInput(value: String) {
        _state.value = _state.value.copy(addInput = value, error = null)
    }

    fun addSymbols() {
        val progression = _state.value.progression ?: return
        val raw = _state.value.addInput
        viewModelScope.launch {
            runCatching { gateway.appendSymbols(progression, raw) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        progression = it,
                        addInput = "",
                        selectedStepIndex = it.steps.lastIndex.coerceAtLeast(0),
                        dirty = true,
                        error = null,
                    )
                    queueDraft(it)
                }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "无法添加和弦。") }
        }
    }

    fun selectStep(index: Int) {
        val last = _state.value.progression?.steps?.lastIndex ?: 0
        _state.value = _state.value.copy(selectedStepIndex = index.coerceIn(0, last.coerceAtLeast(0)))
    }

    fun moveStep(index: Int, delta: Int) {
        val progression = _state.value.progression ?: return
        val target = index + delta
        if (index !in progression.steps.indices || target !in progression.steps.indices) return
        val reordered = progression.steps.toMutableList().apply {
            add(target, removeAt(index))
        }.mapIndexed { order, step -> step.copy(order = order) }
        setSteps(reordered, target)
    }

    fun deleteStep(index: Int) {
        val progression = _state.value.progression ?: return
        if (index !in progression.steps.indices) return
        val updated = progression.steps.toMutableList().apply { removeAt(index) }
            .mapIndexed { order, step -> step.copy(order = order) }
        setSteps(updated, index.coerceAtMost((updated.lastIndex).coerceAtLeast(0)))
    }

    fun updateStep(index: Int, beats: Double, strumPattern: String, voicingId: String) {
        val progression = _state.value.progression ?: return
        if (index !in progression.steps.indices) return
        val safeBeats = beats.coerceIn(0.5, 32.0)
        val updated = progression.steps.toMutableList().apply {
            this[index] = this[index].copy(
                beats = safeBeats,
                strumPattern = strumPattern,
                voicingId = voicingId,
            )
        }
        setSteps(updated, index)
    }

    fun recommend() {
        val progression = _state.value.progression ?: return
        viewModelScope.launch {
            runCatching { gateway.recommend(progression) }
                .onSuccess {
                    _state.value = _state.value.copy(progression = it, dirty = true, error = null)
                    queueDraft(it)
                    effectsChannel.send(ProgressionEditorEffect.Message("已应用本地确定性按法推荐"))
                }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "无法推荐按法。") }
        }
    }

    fun save() {
        val progression = _state.value.progression ?: return
        viewModelScope.launch {
            runCatching { gateway.save(progression) }
                .onSuccess {
                    _state.value = _state.value.copy(progression = it, dirty = false, savingDraft = false, error = null)
                    effectsChannel.send(ProgressionEditorEffect.Message("和弦进行已保存"))
                }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "保存失败。") }
        }
    }

    fun deleteCurrent() {
        val progression = _state.value.progression ?: return
        if (!progression.saved) {
            viewModelScope.launch { effectsChannel.send(ProgressionEditorEffect.NavigateBack) }
            return
        }
        viewModelScope.launch {
            runCatching { gateway.delete(progression.id) }
                .onSuccess { effectsChannel.send(ProgressionEditorEffect.NavigateBack) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "删除失败。") }
        }
    }

    fun togglePlayback() {
        val progression = _state.value.progression ?: return
        runCatching {
            val transportState = transport.state.value
            if (
                transportState.sessionType == PlaybackSessionType.PROGRESSION &&
                transportState.progressionId == progression.id &&
                transportState.status != TransportStatus.STOPPED
            ) {
                transport.toggle()
            } else {
                transport.play(progression)
            }
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "无法开始播放。")
        }
    }

    fun stopPlayback() = transport.stop()
    fun nextStep() = transport.next()
    fun previousStep() = transport.previous()
    fun seekToStep(index: Int) {
        selectStep(index)
        if (transport.state.value.progressionId == _state.value.progression?.id) {
            runCatching { transport.seekToStep(index) }
        }
    }

    private fun setSteps(steps: List<ProgressionStepUi>, selected: Int) {
        mutate(selected) { copy(steps = steps) }
    }

    private fun mutate(
        selectedIndex: Int = _state.value.selectedStepIndex,
        transform: ProgressionUiModel.() -> ProgressionUiModel,
    ) {
        val current = _state.value.progression ?: return
        val updated = current.transform().copy(
            updatedAtEpochMillis = System.currentTimeMillis(),
            restoredDraft = false,
        )
        _state.value = _state.value.copy(
            progression = updated,
            selectedStepIndex = selectedIndex.coerceIn(0, updated.steps.lastIndex.coerceAtLeast(0)),
            dirty = true,
            error = null,
        )
        queueDraft(updated)
    }

    private fun queueDraft(value: ProgressionUiModel) {
        draftChanges.tryEmit(value)
    }
}
