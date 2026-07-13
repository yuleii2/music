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
import com.k2.music.ui.gateway.UserLibraryGateway
import com.k2.music.ui.gateway.ProgressionGateway
import com.k2.music.PracticePreferencesStore
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
import java.util.UUID

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
    val recentSymbols: List<String> = emptyList(),
    val favoriteSymbols: List<String> = emptyList(),
    val familiarSymbols: List<String> = emptyList(),
    val recommendedSymbols: List<String> = listOf("C", "G", "Am", "Em", "D", "A", "Dm", "E"),
    val progressions: List<PracticeProgressionChoice> = emptyList(),
    val weakTransitionSymbols: String? = null,
)

data class PracticeProgressionChoice(
    val id: String,
    val name: String,
    val symbols: String,
    val bpm: Int,
    val timeSignature: String,
)

sealed interface PracticeSetupEffect {
    data class Start(val config: PracticeConfigUi) : PracticeSetupEffect
    data class Message(val text: String) : PracticeSetupEffect
}

class PracticeSetupViewModel(
    private val gateway: PracticeGateway,
    savedStateHandle: SavedStateHandle,
    private val userLibrary: UserLibraryGateway? = null,
    private val progressionGateway: ProgressionGateway? = null,
    private val practicePreferencesStore: PracticePreferencesStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PracticeSetupUiState(config = practiceConfigFrom(savedStateHandle)))
    private val effectsChannel = Channel<PracticeSetupEffect>(Channel.BUFFERED)
    val state: StateFlow<PracticeSetupUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val recent = runCatching { userLibrary?.history().orEmpty() }.getOrDefault(emptyList())
            val favorites = runCatching { userLibrary?.favorites().orEmpty() }.getOrDefault(emptyList())
            val familiar = runCatching {
                practicePreferencesStore?.load()?.familiarVoicingIds.orEmpty()
                    .map { it.substringBefore('|') }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.getOrDefault(emptyList())
            val progressions = runCatching {
                progressionGateway?.list().orEmpty().map {
                    PracticeProgressionChoice(it.id, it.name, it.symbols, it.bpm, it.timeSignature)
                }
            }.getOrDefault(emptyList())
            val weakTransition = runCatching {
                gateway.summary().weakestTransition?.key?.let { "${it.fromChord} ${it.toChord}" }
            }.getOrNull()
            _state.value = _state.value.copy(
                recentSymbols = recent,
                favoriteSymbols = favorites,
                familiarSymbols = familiar,
                progressions = progressions,
                weakTransitionSymbols = weakTransition,
            )
        }
    }

    fun setMode(value: PracticeModeUi) = update { copy(mode = value) }
    fun setSymbols(value: String) = update {
        copy(symbols = value, sourceProgressionId = "", useProgressionRhythm = false)
    }
    fun addSymbol(value: String) {
        val current = selectedSymbols().toMutableList()
        if (value !in current) current += value
        val limited = if (_state.value.config.mode == PracticeModeUi.TWO_CHORD) current.takeLast(2) else current
        setSymbols(limited.joinToString(" "))
    }
    fun removeSymbol(value: String) = setSymbols(selectedSymbols().filterNot { it == value }.joinToString(" "))
    fun useProgression(value: PracticeProgressionChoice) = update {
        copy(
            mode = PracticeModeUi.MULTI_CHORD,
            symbols = value.symbols,
            bpm = value.bpm.coerceIn(40, 240),
            timeSignature = value.timeSignature,
            sourceProgressionId = value.id,
            useProgressionRhythm = true,
        )
    }
    fun setDuration(value: Int) = update { copy(durationSeconds = value.coerceIn(5, 3600)) }
    fun setBpm(value: Int) = update { copy(bpm = value.coerceIn(40, 240)) }
    fun setTimeSignature(value: String) = update { copy(timeSignature = value) }
    fun setSwitchMode(value: PracticeSwitchUi) = update {
        copy(switchMode = value, useProgressionRhythm = false)
    }
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

    private fun selectedSymbols(): List<String> = _state.value.config.symbols.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .distinct()
}

fun interface PracticeElapsedClock {
    fun elapsedRealtimeMillis(): Long
    fun nanoTime(): Long = elapsedRealtimeMillis() * 1_000_000L
}

data class PracticeSessionUiState(
    val loading: Boolean = true,
    val config: PracticeConfigUi = PracticeConfigUi(),
    val progression: ProgressionUiModel? = null,
    val remainingMillis: Long = 0,
    val paused: Boolean = false,
    val completionCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val recordingResult: Boolean = false,
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
    private val clock: PracticeElapsedClock = object : PracticeElapsedClock {
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
        override fun nanoTime(): Long = SystemClock.elapsedRealtimeNanos()
    },
) : ViewModel() {
    private val config = practiceConfigFrom(savedStateHandle)
    private val sessionId = savedStateHandle.get<String>(KEY_SESSION_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_SESSION_ID] = it }
    private val startedAtEpochMillis = savedStateHandle.get<Long>(KEY_STARTED_AT)
        ?: System.currentTimeMillis().also { savedStateHandle[KEY_STARTED_AT] = it }
    private val fullDurationMillis = config.durationSeconds * 1_000L
    private val effectsChannel = Channel<PracticeSessionEffect>(Channel.BUFFERED)
    private val _state = MutableStateFlow(
        PracticeSessionUiState(
            config = config,
            remainingMillis = savedStateHandle[KEY_REMAINING] ?: fullDurationMillis,
            paused = savedStateHandle[KEY_PAUSED] ?: false,
            completionCount = savedStateHandle[KEY_COUNT] ?: 0,
            successCount = savedStateHandle[KEY_SUCCESS_COUNT] ?: 0,
            failureCount = savedStateHandle[KEY_FAILURE_COUNT] ?: 0,
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
                    val restored = runCatching { gateway.sessionProgress(sessionId) }.getOrDefault(
                        com.k2.music.ui.gateway.AttemptProgressUi(),
                    )
                    persistProgress(restored)
                    _state.value = _state.value.copy(
                        loading = false,
                        progression = progression,
                        completionCount = restored.attemptCount,
                        successCount = restored.successCount,
                        failureCount = restored.failureCount,
                        currentStreak = restored.currentStreak,
                        bestStreak = restored.bestStreak,
                    )
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

    fun recordSuccess() = recordAttempt(success = true)

    fun recordFailure() = recordAttempt(success = false)

    private fun recordAttempt(success: Boolean) {
        val currentState = _state.value
        if (currentState.paused || currentState.finishing || currentState.recordingResult) return
        val progression = currentState.progression ?: return
        val playback = transport.state.value
        if (playback.status != TransportStatus.PLAYING || playback.stepIndex !in progression.steps.indices) return
        val toIndex = playback.stepIndex
        val fromIndex = (toIndex - 1 + progression.steps.size) % progression.steps.size
        val from = progression.steps[fromIndex]
        val to = progression.steps[toIndex]
        val confirmationOffset = playback.stepAnchorNanos
            .takeIf { it > 0L }
            ?.let { (clock.nanoTime() - it) / 1_000_000L }
        _state.value = currentState.copy(recordingResult = true, error = null)
        viewModelScope.launch {
            runCatching {
                gateway.recordAttempt(
                    sessionId,
                    config,
                    from.chordSymbol,
                    to.chordSymbol,
                    from.voicingId.takeIf { it.isNotBlank() },
                    to.voicingId.takeIf { it.isNotBlank() },
                    success,
                    confirmationOffset,
                )
            }.onSuccess { progress ->
                persistProgress(progress)
                _state.value = _state.value.copy(
                    completionCount = progress.attemptCount,
                    successCount = progress.successCount,
                    failureCount = progress.failureCount,
                    currentStreak = progress.currentStreak,
                    bestStreak = progress.bestStreak,
                    recordingResult = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    recordingResult = false,
                    error = it.message ?: "本次结果未能保存，请再试一次。",
                )
            }
        }
    }

    fun reset() {
        transport.stop()
        ticker?.cancel()
        savedStateHandle[KEY_REMAINING] = fullDurationMillis
        savedStateHandle[KEY_COUNT] = 0
        savedStateHandle[KEY_SUCCESS_COUNT] = 0
        savedStateHandle[KEY_FAILURE_COUNT] = 0
        savedStateHandle[KEY_CURRENT_STREAK] = 0
        savedStateHandle[KEY_BEST_STREAK] = 0
        savedStateHandle[KEY_PAUSED] = false
        _state.value = _state.value.copy(
            remainingMillis = fullDurationMillis,
            paused = false,
            completionCount = 0,
            successCount = 0,
            failureCount = 0,
            currentStreak = 0,
            bestStreak = 0,
            recordingResult = true,
            error = null,
        )
        viewModelScope.launch {
            runCatching { gateway.discardSession(sessionId) }
            _state.value = _state.value.copy(recordingResult = false)
            resume()
        }
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
                    sessionId,
                    startedAtEpochMillis,
                    config,
                    actualSeconds,
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
        viewModelScope.launch { runCatching { gateway.discardSession(sessionId) } }
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

    private fun persistProgress(progress: com.k2.music.ui.gateway.AttemptProgressUi) {
        savedStateHandle[KEY_COUNT] = progress.attemptCount
        savedStateHandle[KEY_SUCCESS_COUNT] = progress.successCount
        savedStateHandle[KEY_FAILURE_COUNT] = progress.failureCount
        savedStateHandle[KEY_CURRENT_STREAK] = progress.currentStreak
        savedStateHandle[KEY_BEST_STREAK] = progress.bestStreak
    }

    private companion object {
        const val KEY_REMAINING = "practice_remaining"
        const val KEY_PAUSED = "practice_paused"
        const val KEY_COUNT = "practice_count"
        const val KEY_SUCCESS_COUNT = "practice_success_count"
        const val KEY_FAILURE_COUNT = "practice_failure_count"
        const val KEY_CURRENT_STREAK = "practice_current_streak"
        const val KEY_BEST_STREAK = "practice_best_streak"
        const val KEY_SESSION_ID = "practice_session_id"
        const val KEY_STARTED_AT = "practice_started_at"
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
    sourceProgressionId = handle.get<String>("progressionId").orEmpty(),
    useProgressionRhythm = handle["progressionRhythm"] ?: false,
)

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
