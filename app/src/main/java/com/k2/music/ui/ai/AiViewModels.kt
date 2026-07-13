package com.k2.music.ui.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.AiAcceptResult
import com.k2.music.ui.gateway.AiFailureUi
import com.k2.music.ui.gateway.AiGateway
import com.k2.music.ui.gateway.AiGatewayException
import com.k2.music.ui.gateway.AiResultUi
import com.k2.music.ui.gateway.AiSettingsUi
import com.k2.music.ui.gateway.AiTaskUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AiAssistantUiState(
    val configured: Boolean = false,
    val task: AiTaskUi = AiTaskUi.RECOMMEND_CHORDS,
    val input: String = "",
    val contextSymbol: String = "",
    val loading: Boolean = false,
    val result: AiResultUi? = null,
    val error: AiFailureUi? = null,
)

sealed interface AiAssistantEffect {
    data class OpenProgression(val seed: String) : AiAssistantEffect
    data class OpenPractice(val title: String) : AiAssistantEffect
    data class Message(val text: String) : AiAssistantEffect
}

class AiAssistantViewModel(
    private val gateway: AiGateway,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialSymbol = savedStateHandle.get<String>("symbol").orEmpty()
    private val initialTask = parseTask(savedStateHandle.get<String>("mode"), initialSymbol)
    private val _state = MutableStateFlow(
        AiAssistantUiState(
            configured = gateway.isConfigured(),
            task = initialTask,
            input = initialSymbol,
            contextSymbol = initialSymbol,
        ),
    )
    private val effectsChannel = Channel<AiAssistantEffect>(Channel.BUFFERED)
    private var requestJob: Job? = null
    val state: StateFlow<AiAssistantUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    fun refreshConfiguration() {
        _state.value = _state.value.copy(configured = gateway.isConfigured())
    }

    fun setTask(value: AiTaskUi) {
        _state.value = _state.value.copy(task = value, result = null, error = null)
    }

    fun setInput(value: String) {
        _state.value = _state.value.copy(input = value, error = null)
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.input.isBlank()) {
            _state.value = snapshot.copy(error = AiFailureUi("INPUT", "请输入任务内容。"))
            return
        }
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = snapshot.copy(loading = true, result = null, error = null)
            runCatching { gateway.submit(snapshot.task, snapshot.input, snapshot.contextSymbol) }
                .onSuccess { _state.value = _state.value.copy(loading = false, result = it) }
                .onFailure { error ->
                    val failure = (error as? AiGatewayException)?.failure
                        ?: AiFailureUi("UNKNOWN", error.message ?: "AI 请求失败。")
                    _state.value = _state.value.copy(loading = false, error = failure)
                }
        }
    }

    fun cancel() {
        requestJob?.cancel()
        requestJob = null
        gateway.cancel()
        _state.value = _state.value.copy(loading = false)
    }

    fun accept() {
        val result = _state.value.result ?: return
        when (val accepted = gateway.accept(result)) {
            is AiAcceptResult.OpenProgression -> viewModelScope.launch {
                effectsChannel.send(AiAssistantEffect.OpenProgression(accepted.seed))
            }
            is AiAcceptResult.OpenPractice -> viewModelScope.launch {
                effectsChannel.send(AiAssistantEffect.OpenPractice(accepted.title))
            }
            AiAcceptResult.None -> viewModelScope.launch {
                effectsChannel.send(AiAssistantEffect.Message("当前结果没有可保存的本地草稿。"))
            }
        }
    }

    override fun onCleared() {
        gateway.cancel()
    }

    private fun parseTask(raw: String?, symbol: String): AiTaskUi = when (raw?.lowercase()) {
        "explain", "explain_chord" -> AiTaskUi.EXPLAIN_CHORD
        "progression" -> AiTaskUi.GENERATE_PROGRESSION
        "optimize" -> AiTaskUi.OPTIMIZE_PROGRESSION
        "practice" -> AiTaskUi.PRACTICE_PLAN
        "transition" -> AiTaskUi.TRANSITION_ADVICE
        "mood" -> AiTaskUi.MOOD_PROGRESSION
        else -> if (symbol.isNotBlank()) AiTaskUi.EXPLAIN_CHORD else AiTaskUi.RECOMMEND_CHORDS
    }
}

data class AiSettingsUiState(
    val settings: AiSettingsUi = AiSettingsUi(),
    val saving: Boolean = false,
    val testing: Boolean = false,
    val error: String? = null,
)

sealed interface AiSettingsEffect {
    data class Message(val text: String) : AiSettingsEffect
    data object ClearKeyField : AiSettingsEffect
}

class AiSettingsViewModel(private val gateway: AiGateway) : ViewModel() {
    private val _state = MutableStateFlow(AiSettingsUiState(settings = gateway.settings()))
    private val effectsChannel = Channel<AiSettingsEffect>(Channel.BUFFERED)
    private var testJob: Job? = null
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    fun setEnabled(value: Boolean) = update { copy(enabled = value) }
    fun setService(value: String) = update { copy(serviceName = value) }
    fun setBaseUrl(value: String) = update { copy(baseUrl = value) }
    fun setModel(value: String) = update { copy(model = value) }
    fun setTemperature(value: Double) = update { copy(temperature = value.coerceIn(0.0, 2.0)) }
    fun setTimeout(value: Int) = update { copy(timeoutSeconds = value.coerceIn(5, 120)) }

    fun save(apiKey: String) {
        val settings = _state.value.settings
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching { gateway.saveSettings(settings, apiKey.takeIf { it.isNotBlank() }) }
                .onSuccess {
                    _state.value = AiSettingsUiState(settings = gateway.settings())
                    effectsChannel.send(AiSettingsEffect.ClearKeyField)
                    effectsChannel.send(AiSettingsEffect.Message("AI 设置已安全保存"))
                }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "保存失败。") }
        }
    }

    fun testConnection() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _state.value = _state.value.copy(testing = true, error = null)
            runCatching { gateway.testConnection() }
                .onSuccess {
                    _state.value = _state.value.copy(testing = false)
                    effectsChannel.send(AiSettingsEffect.Message(it))
                }
                .onFailure {
                    _state.value = _state.value.copy(testing = false, error = it.message ?: "连接测试失败。")
                }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        gateway.cancel()
        _state.value = _state.value.copy(testing = false)
    }

    fun clearConfiguration() {
        gateway.clearSettings()
        _state.value = AiSettingsUiState(settings = gateway.settings())
        viewModelScope.launch {
            effectsChannel.send(AiSettingsEffect.ClearKeyField)
            effectsChannel.send(AiSettingsEffect.Message("AI 配置已清除"))
        }
    }

    fun clearCache() {
        gateway.clearCache()
        viewModelScope.launch { effectsChannel.send(AiSettingsEffect.Message("AI 本地缓存已清除")) }
    }

    private fun update(transform: AiSettingsUi.() -> AiSettingsUi) {
        _state.value = _state.value.copy(settings = _state.value.settings.transform(), error = null)
    }
}
