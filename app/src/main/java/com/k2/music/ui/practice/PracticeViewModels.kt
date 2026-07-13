package com.k2.music.ui.practice

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeHomeData
import com.k2.music.ui.gateway.PracticeModeUi
import com.k2.music.ui.gateway.PracticeResultUi
import com.k2.music.ui.gateway.PracticeSwitchUi
import com.k2.music.ui.gateway.ProgressionTransport
import com.k2.music.ui.gateway.TransportStatus
import com.k2.music.ui.model.ProgressionUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PracticeHomeUiState(
    val loading: Boolean = true,
    val data: PracticeHomeData? = null,
    val error: String? = null,
)

class PracticeHomeViewModel(private val gateway: PracticeGateway) : ViewModel() {
    private val _state = MutableStateFlow(PracticeHomeUiState())
    val state: StateFlow<PracticeHomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PracticeHomeUiState(loading = true)
            runCatching { gateway.home() }
                .onSuccess { _state.value = PracticeHomeUiState(loading = false, data = it) }
                .onFailure { _state.value = PracticeHomeUiState(loading = false, error = it.message ?: "无法读取练习记录。") }
        }
    }
}

data class PracticeSetupUiState(
    val config: PracticeConfigUi = PracticeConfigUi(),
    val validating: Boolean = false,
    val error: String? = null,
)

sealed interface PracticeSetupEffect {
    data class Start(val config: PracticeConfigUi) : PracticeSetupEffect
    data class Message(val text: String) : PracticeSetupEffect
}

class PracticeSetupViewModel(
    private val gateway: PracticeGateway,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(PracticeSetupUiState(config = practiceConfigFrom(savedStateHandle)))
    private val effectsChannel = Channel<PracticeSetupEffect>(Channel.BUFFERED)
    val state: StateFlow<PracticeSetupUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    fun setMode(value: PracticeModeUi) = update { copy(mode = value) }
    fun setSymbols(value: String) = update { copy(symbols = value) }
    fun setDuration(value: Int) = update { copy(durationSeconds = value.coerceIn(5, 3600)) }
    fun setBpm(value: Int) = update { copy(bpm = value.coerceIn(40, 240)) }
    fun setTimeSignature(value: String) = update { copy(timeSignature = value) }
    fun setSwitchMode(value: PracticeSwitchUi) = update { copy(switchMode = value) }
    fun setAccent(value: Boolean) = update { copy(accentFirstBeat = value) }
    fun setAllowBarre(value: Boolean) = update { copy(allowBarre = value) }
    fun setMaxFret(value: Int) = update { copy(maxFret = value.coerceIn(1, 24)) }

    fun start() {
        val config = _state.value.config
        viewModelScope.launch {
            _state.value = _state.value.copy(validating = true, error = null)
            runCatching {
                gateway.prepare(config)
                gateway.savePreferences(config)
            }.onSuccess {
                _state.value = _state.value.copy(validating = false)
                effectsChannel.send(PracticeSetupEffect.Start(config))
            }.onFailure {
                _state.value = _state.value.copy(validating = false, error = it.message ?: "练习设置无效。")
            }
        }
    }

    private fun update(transform: PracticeConfigUi.() -> PracticeConfigUi) {
        _state.value = _state.value.copy(config = _state.value.config.transform(), error = null)
    }
}

fun interface PracticeElapsedClock { fun elapsedRealtimeMillis(): Long }

data class PracticeSessionUiState(
    val loading: Boolean = true,
    val config: PracticeConfigUi = PracticeConfigUi(),
    val progression: ProgressionUiModel? = null,
    val remainingMillis: Long = 0,
    val paused: Boolean = false,
    val completionCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val finishing: Boolean = false,
    val error: String? = null,
)

sealed interface PracticeSessionEffect {
    data class Finished(val result: PracticeResultUi, val config: PracticeConfigUi) : PracticeSessionEffect
    data class Message(val text: String) : PracticeSessionEffect
}

class PracticeSessionViewModel(
    private val gateway: PracticeGateway,
    private val transport: ProgressionTransport,
    private val savedStateHandle: SavedStateHandle,
    private val clock: PracticeElapsedClock = PracticeElapsedClock(SystemClock::elapsedRealtime),
) : ViewModel() {
    private val config = practiceConfigFrom(savedStateHandle)
    private val fullDurationMillis = config.durationSeconds * 1_000L
    private val effectsChannel = Channel<PracticeSessionEffect>(Channel.BUFFERED)
    private val _state = MutableStateFlow(
        PracticeSessionUiState(
            config = config,
            remainingMillis = savedStateHandle[KEY_REMAINING] ?: fullDurationMillis,
            paused = savedStateHandle[KEY_PAUSED] ?: false,
            completionCount = savedStateHandle[KEY_COUNT] ?: 0,
            currentStreak = savedStateHandle[KEY_CURRENT_STREAK] ?: 0,
            bestStreak = savedStateHandle[KEY_BEST_STREAK] ?: 0,
        ),
    )
    private var ticker: Job? = null
    private var anchorElapsedMillis = 0L
    private var anchorRemainingMillis = _state.value.remainingMillis
    private var resultSaved = false

    val state: StateFlow<PracticeSessionUiState> = _state.asStateFlow()
    val playback = transport.state
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching { gateway.prepare(config) }
                .onSuccess { progression ->
                    _state.value = _state.value.copy(loading = false, progression = progression)
                    if (!_state.value.paused && _state.value.remainingMillis > 0) resume()
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message ?: "无法开始练习。")
                }
        }
    }

    fun togglePause() {
        if (_state.value.paused) resume() else pause()
    }

    fun pause() {
        if (_state.value.paused || _state.value.finishing) return
        if (_state.value.loading) {
            savedStateHandle[KEY_PAUSED] = true
            _state.value = _state.value.copy(paused = true)
            return
        }
        updateRemaining()
        ticker?.cancel()
        ticker = null
        transport.pause()
        savedStateHandle[KEY_PAUSED] = true
        _state.value = _state.value.copy(paused = true)
    }

    fun resume() {
        val progression = _state.value.progression ?: return
        if (_state.value.remainingMillis <= 0 || _state.value.finishing) return
        val transportState = transport.state.value
        if (
            transportState.sessionType == PlaybackSessionType.PROGRESSION &&
            transportState.progressionId == progression.id &&
            transportState.status == TransportStatus.PAUSED
        ) {
            transport.toggle()
        } else {
            transport.play(progression)
        }
        anchorElapsedMillis = clock.elapsedRealtimeMillis()
        anchorRemainingMillis = _state.value.remainingMillis
        savedStateHandle[KEY_PAUSED] = false
        _state.value = _state.value.copy(paused = false)
        startTicker()
    }

    fun completeOnce() {
        if (_state.value.paused || _state.value.finishing) return
        val count = _state.value.completionCount + 1
        val streak = _state.value.currentStreak + 1
        val best = maxOf(_state.value.bestStreak, streak)
        savedStateHandle[KEY_COUNT] = count
        savedStateHandle[KEY_CURRENT_STREAK] = streak
        savedStateHandle[KEY_BEST_STREAK] = best
        _state.value = _state.value.copy(completionCount = count, currentStreak = streak, bestStreak = best)
    }

    fun reset() {
        transport.stop()
        ticker?.cancel()
        savedStateHandle[KEY_REMAINING] = fullDurationMillis
        savedStateHandle[KEY_COUNT] = 0
        savedStateHandle[KEY_CURRENT_STREAK] = 0
        savedStateHandle[KEY_BEST_STREAK] = 0
        savedStateHandle[KEY_PAUSED] = false
        _state.value = _state.value.copy(
            remainingMillis = fullDurationMillis,
            paused = false,
            completionCount = 0,
            currentStreak = 0,
            bestStreak = 0,
            error = null,
        )
        resume()
    }

    fun finish() {
        if (resultSaved || _state.value.finishing) return
        updateRemaining()
        ticker?.cancel()
        transport.stop()
        val actualSeconds = ((fullDurationMillis - _state.value.remainingMillis) / 1_000L).toInt()
        _state.value = _state.value.copy(finishing = true, paused = true)
        viewModelScope.launch {
            runCatching {
                gateway.saveResult(
                    config,
                    actualSeconds,
                    _state.value.completionCount,
                    _state.value.bestStreak,
                )
            }.onSuccess {
                resultSaved = true
                effectsChannel.send(PracticeSessionEffect.Finished(it, config))
            }.onFailure {
                _state.value = _state.value.copy(finishing = false, error = it.message ?: "练习结果保存失败。")
                effectsChannel.send(PracticeSessionEffect.Message("结果未保存，但当前总结仍保留在屏幕上。"))
            }
        }
    }

    fun abandon() {
        pause()
        transport.stop()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && !_state.value.paused && !_state.value.finishing) {
                updateRemaining()
                if (_state.value.remainingMillis <= 0) {
                    finish()
                    break
                }
                delay(100)
            }
        }
    }

    private fun updateRemaining() {
        if (_state.value.paused || anchorElapsedMillis == 0L) return
        val elapsed = (clock.elapsedRealtimeMillis() - anchorElapsedMillis).coerceAtLeast(0L)
        val remaining = (anchorRemainingMillis - elapsed).coerceAtLeast(0L)
        savedStateHandle[KEY_REMAINING] = remaining
        _state.value = _state.value.copy(remainingMillis = remaining)
    }

    override fun onCleared() {
        ticker?.cancel()
    }

    private companion object {
        const val KEY_REMAINING = "practice_remaining"
        const val KEY_PAUSED = "practice_paused"
        const val KEY_COUNT = "practice_count"
        const val KEY_CURRENT_STREAK = "practice_current_streak"
        const val KEY_BEST_STREAK = "practice_best_streak"
    }
}

internal fun practiceConfigFrom(handle: SavedStateHandle) = PracticeConfigUi(
    mode = enumValue(handle["mode"], PracticeModeUi.TWO_CHORD),
    symbols = handle.get<String>("symbols").orEmpty().ifBlank { "C G" },
    durationSeconds = (handle["duration"] ?: 60).coerceIn(5, 3600),
    bpm = (handle["bpm"] ?: 80).coerceIn(40, 240),
    timeSignature = handle.get<String>("signature").orEmpty().ifBlank { "4/4" },
    switchMode = enumValue(handle["switch"], PracticeSwitchUi.EACH_MEASURE),
    accentFirstBeat = handle["accent"] ?: true,
    allowBarre = handle["barre"] ?: true,
    maxFret = (handle["maxFret"] ?: 12).coerceIn(1, 24),
)

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
