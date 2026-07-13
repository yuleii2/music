package com.k2.music.ui.progression

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.ProgressionGateway
import com.k2.music.ui.model.ProgressionPresetUi
import com.k2.music.ui.model.ProgressionSummaryUi
import com.k2.music.ui.model.ProgressionUiModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ProgressionListUiState(
    val loading: Boolean = true,
    val saved: List<ProgressionSummaryUi> = emptyList(),
    val presets: List<ProgressionPresetUi> = emptyList(),
    val presetKey: String = "C",
    val error: String? = null,
)

sealed interface ProgressionListEffect {
    data class OpenEditor(val id: String) : ProgressionListEffect
    data class Message(val text: String) : ProgressionListEffect
    data class Deleted(val name: String) : ProgressionListEffect
}
class ProgressionListViewModel(
    private val gateway: ProgressionGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ProgressionListUiState(presetKey = savedStateHandle[KEY_PRESET_KEY] ?: "C"),
    )
    private val effectsChannel = Channel<ProgressionListEffect>(Channel.BUFFERED)
    private var deleted: ProgressionUiModel? = null

    val state: StateFlow<ProgressionListUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                gateway.list() to gateway.presets(_state.value.presetKey)
            }.onSuccess { (saved, presets) ->
                _state.value = _state.value.copy(
                    loading = false,
                    saved = saved,
                    presets = presets,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "无法读取本地和弦进行。",
                )
            }
        }
    }

    fun setPresetKey(value: String) {
        savedStateHandle[KEY_PRESET_KEY] = value
        _state.value = _state.value.copy(presetKey = value)
        refresh()
    }

    fun create(seed: String = "") {
        viewModelScope.launch {
            runCatching {
                gateway.createDraft(seed).also { gateway.saveDraft(it) }
            }.onSuccess { effectsChannel.send(ProgressionListEffect.OpenEditor(it.id)) }
                .onFailure { effectsChannel.send(ProgressionListEffect.Message(it.message ?: "无法新建进行。")) }
        }
    }

    fun createFromPreset(presetId: String) {
        viewModelScope.launch {
            runCatching { gateway.createPresetDraft(presetId, _state.value.presetKey) }
                .onSuccess { effectsChannel.send(ProgressionListEffect.OpenEditor(it.id)) }
                .onFailure { effectsChannel.send(ProgressionListEffect.Message(it.message ?: "无法载入预设。")) }
        }
    }

    fun duplicate(item: ProgressionSummaryUi) {
        viewModelScope.launch {
            runCatching { gateway.duplicate(item.id, "${item.name} 副本") }
                .onSuccess {
                    refresh()
                    effectsChannel.send(ProgressionListEffect.Message("已复制 ${item.name}"))
                }
                .onFailure { effectsChannel.send(ProgressionListEffect.Message(it.message ?: "复制失败。")) }
        }
    }

    fun rename(item: ProgressionSummaryUi, name: String) {
        viewModelScope.launch {
            runCatching { gateway.rename(item.id, name) }
                .onSuccess {
                    refresh()
                    effectsChannel.send(ProgressionListEffect.Message("已重命名为 ${it.name}"))
                }
                .onFailure { effectsChannel.send(ProgressionListEffect.Message(it.message ?: "重命名失败。")) }
        }
    }

    fun delete(item: ProgressionSummaryUi) {
        viewModelScope.launch {
            runCatching { gateway.delete(item.id) }
                .onSuccess { removed ->
                    deleted = removed
                    refresh()
                    if (removed != null) effectsChannel.send(ProgressionListEffect.Deleted(item.name))
                }
                .onFailure { effectsChannel.send(ProgressionListEffect.Message(it.message ?: "删除失败。")) }
        }
    }

    fun undoDelete() {
        val value = deleted ?: return
        deleted = null
        viewModelScope.launch {
            runCatching { gateway.restore(value) }
                .onSuccess {
                    refresh()
                    effectsChannel.send(ProgressionListEffect.Message("已恢复 ${it.name}"))
                }
                .onFailure { effectsChannel.send(ProgressionListEffect.Message(it.message ?: "恢复失败。")) }
        }
    }

    private companion object {
        const val KEY_PRESET_KEY = "progression_preset_key"
    }
}
