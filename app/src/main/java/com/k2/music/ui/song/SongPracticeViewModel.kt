package com.k2.music.ui.song

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongPracticeRun
import com.k2.music.song.SongTransition
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.ProgressionPlaybackUiState
import com.k2.music.ui.gateway.ProgressionTransport
import com.k2.music.ui.gateway.TransportStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

interface SongPracticeClock {
    fun elapsedRealtimeMillis(): Long
    fun currentTimeMillis(): Long
}

data class SongPracticeUiState(
    val loading: Boolean = true,
    val preparation: SongPracticePreparation? = null,
    val started: Boolean = false,
    val paused: Boolean = true,
    val elapsedMillis: Long = 0L,
    val currentBpm: Int = 80,
    val loop: Boolean = true,
    val manualStepIndex: Int = 0,
    val showFretboard: Boolean = true,
    val reviewing: Boolean = false,
    val completed: Boolean = false,
    val selectedDifficulties: Set<SongTransition> = emptySet(),
    val saving: Boolean = false,
    val savedRun: SongPracticeRun? = null,
    val error: String? = null,
)

sealed interface SongPracticeEffect { data object Abandoned : SongPracticeEffect }

class SongPracticeViewModel(
    private val gateway: SongGateway,
    private val transport: ProgressionTransport,
    private val savedStateHandle: SavedStateHandle,
    private val clock: SongPracticeClock = object : SongPracticeClock {
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    },
) : ViewModel() {
    private val songId: String = savedStateHandle["id"] ?: ""
    private val sectionId: String? = savedStateHandle.get<String>("sectionId")?.ifBlank { null }
    private val restoredBpm = savedStateHandle.get<Int>("restoreBpm")?.takeIf { it in 40..240 }
    private val restoredTranspose = savedStateHandle.get<Int>("restoreTranspose")?.takeIf { it in -11..11 }
    private val restoredCapo = savedStateHandle.get<Int>("restoreCapo")?.takeIf { it in 0..12 }
    private val restoredLoop = savedStateHandle.get<Int>("restoreLoop")?.takeIf { it in 0..1 }?.let { it == 1 }
    private val restoredFretboard = savedStateHandle.get<Int>("restoreFretboard")?.takeIf { it in 0..1 }?.let { it == 1 }
    private val _state = MutableStateFlow(
        SongPracticeUiState(
            started = savedStateHandle[KEY_STARTED] ?: false,
            paused = savedStateHandle[KEY_PAUSED] ?: true,
            elapsedMillis = savedStateHandle[KEY_ELAPSED] ?: 0L,
            manualStepIndex = savedStateHandle[KEY_MANUAL_INDEX] ?: 0,
            reviewing = savedStateHandle[KEY_REVIEWING] ?: false,
            completed = savedStateHandle[KEY_COMPLETED] ?: false,
            loop = savedStateHandle[KEY_LOOP] ?: restoredLoop ?: true,
            showFretboard = savedStateHandle[KEY_SHOW_FRETBOARD] ?: restoredFretboard ?: true,
            selectedDifficulties = decodeSongDifficulties(savedStateHandle[KEY_DIFFICULTIES] ?: ""),
        ),
    )
    val state = _state.asStateFlow()
    val playback = transport.state
    private val _effects = MutableSharedFlow<SongPracticeEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()
    private var ticker: Job? = null
    private val runId: String = savedStateHandle.get<String>(KEY_RUN_ID) ?: UUID.randomUUID().toString().also {
        savedStateHandle[KEY_RUN_ID] = it
    }
    private var elapsedAnchor = 0L
    private var accumulatedAtAnchor = _state.value.elapsedMillis

    init {
        viewModelScope.launch {
            runCatching {
                if (restoredBpm != null || restoredTranspose != null || restoredCapo != null) {
                    gateway.restorePracticeConfiguration(
                        songId = songId,
                        bpm = restoredBpm ?: 80,
                        transposeSemitones = restoredTranspose ?: 0,
                        capoFret = restoredCapo ?: 0,
                    )
                }
                gateway.preparePractice(songId, sectionId)
            }
                .onSuccess { preparation ->
                    val bpm = (savedStateHandle[KEY_BPM] ?: restoredBpm ?: preparation.project.bpm).coerceIn(40, 240)
                    _state.update {
                        it.copy(
                            loading = false,
                            preparation = preparation,
                            currentBpm = bpm,
                            manualStepIndex = it.manualStepIndex.coerceIn(preparation.progression.steps.indices),
                        )
                    }
                    if (_state.value.started && !_state.value.paused && !_state.value.reviewing) resume()
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage("无法开始曲谱练习。")) }
                }
        }
    }

    fun startOrTogglePause() {
        if (_state.value.savedRun != null || _state.value.reviewing) return
        if (!_state.value.started || _state.value.paused) resume() else pause()
    }

    fun pause() {
        if (_state.value.paused) return
        updateElapsed()
        ticker?.cancel()
        transport.pause()
        savedStateHandle[KEY_PAUSED] = true
        _state.update { it.copy(paused = true) }
    }

    private fun resume() {
        val preparation = _state.value.preparation ?: return
        if (_state.value.reviewing) return
        val wasStarted = _state.value.started
        val currentPlayback = transport.state.value
        if (preparation.preciseTiming) {
            if (
                currentPlayback.sessionType == PlaybackSessionType.PROGRESSION &&
                currentPlayback.progressionId == preparation.progression.id &&
                currentPlayback.status == TransportStatus.PAUSED
            ) {
                transport.toggle()
            } else {
                transport.play(preparation.progression.copy(bpm = _state.value.currentBpm, loop = _state.value.loop))
            }
        } else {
            if (currentPlayback.sessionType == PlaybackSessionType.METRONOME && currentPlayback.status == TransportStatus.PAUSED) {
                transport.toggle()
            } else {
                transport.startMetronome(
                    _state.value.currentBpm,
                    preparation.project.timeSignature,
                    accentFirstBeat = true,
                )
            }
        }
        if (!wasStarted) {
            savedStateHandle[KEY_STARTED_AT] = clock.currentTimeMillis()
            savedStateHandle[KEY_STARTED] = true
        }
        elapsedAnchor = clock.elapsedRealtimeMillis()
        accumulatedAtAnchor = _state.value.elapsedMillis
        savedStateHandle[KEY_PAUSED] = false
        _state.update { it.copy(started = true, paused = false, error = null) }
        startTicker()
    }

    fun next() {
        val preparation = _state.value.preparation ?: return
        if (preparation.preciseTiming) {
            transport.next()
        } else {
            val next = (_state.value.manualStepIndex + 1).let { index ->
                if (index < preparation.progression.steps.size) index else if (_state.value.loop) 0 else preparation.progression.steps.lastIndex
            }
            savedStateHandle[KEY_MANUAL_INDEX] = next
            _state.update { it.copy(manualStepIndex = next) }
        }
    }

    fun previous() {
        val preparation = _state.value.preparation ?: return
        if (preparation.preciseTiming) {
            transport.previous()
        } else {
            val previous = (_state.value.manualStepIndex - 1).let { index ->
                if (index >= 0) index else if (_state.value.loop) preparation.progression.steps.lastIndex else 0
            }
            savedStateHandle[KEY_MANUAL_INDEX] = previous
            _state.update { it.copy(manualStepIndex = previous) }
        }
    }

    fun setLoop(value: Boolean) {
        savedStateHandle[KEY_LOOP] = value
        _state.update { it.copy(loop = value) }
        if (_state.value.preparation?.preciseTiming == true) transport.updateLoop(value)
    }

    fun adjustBpm(delta: Int) {
        val bpm = (_state.value.currentBpm + delta).coerceIn(40, 240)
        savedStateHandle[KEY_BPM] = bpm
        _state.update { it.copy(currentBpm = bpm) }
        if (_state.value.started) transport.updateBpm(bpm)
    }

    fun toggleFretboard() {
        val value = !_state.value.showFretboard
        savedStateHandle[KEY_SHOW_FRETBOARD] = value
        _state.update { it.copy(showFretboard = value) }
    }

    fun finishForReview() {
        if (!_state.value.started || _state.value.reviewing) return
        pause()
        transport.stop()
        savedStateHandle[KEY_REVIEWING] = true
        _state.update { it.copy(reviewing = true, paused = true) }
    }

    fun toggleCompleted() {
        val value = !_state.value.completed
        savedStateHandle[KEY_COMPLETED] = value
        _state.update { it.copy(completed = value) }
    }

    fun toggleDifficulty(transition: SongTransition) {
        _state.update { state ->
            val selected = state.selectedDifficulties.toMutableSet().apply {
                if (!add(transition)) remove(transition)
            }
            savedStateHandle[KEY_DIFFICULTIES] = encodeSongDifficulties(selected)
            state.copy(selectedDifficulties = selected)
        }
    }

    fun saveReview() {
        val preparation = _state.value.preparation ?: return
        if (!_state.value.reviewing || _state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val startedAt = savedStateHandle[KEY_STARTED_AT] ?: clock.currentTimeMillis()
            val endedAt = clock.currentTimeMillis().coerceAtLeast(startedAt)
            runCatching {
                gateway.savePracticeRun(
                    songId = songId,
                    sectionId = sectionId,
                    mode = SongPracticeMode.PERFORMANCE,
                    bpm = _state.value.currentBpm,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    actualDurationSeconds = (_state.value.elapsedMillis / 1_000L).toInt(),
                    completed = _state.value.completed,
                    difficultTransitions = _state.value.selectedDifficulties.toList(),
                    runId = runId,
                    loopEnabled = _state.value.loop,
                    showFretboard = _state.value.showFretboard,
                )
            }.onSuccess { run ->
                _state.update { it.copy(saving = false, savedRun = run) }
            }.onFailure { error ->
                _state.update { it.copy(saving = false, error = error.userMessage("无法保存连续演奏记录。")) }
            }
        }
    }

    fun abandon() {
        ticker?.cancel()
        transport.stop()
        _effects.tryEmit(SongPracticeEffect.Abandoned)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && !_state.value.paused && !_state.value.reviewing) {
                updateElapsed()
                delay(100L)
            }
        }
    }

    private fun updateElapsed() {
        if (_state.value.paused || elapsedAnchor == 0L) return
        val elapsed = accumulatedAtAnchor + (clock.elapsedRealtimeMillis() - elapsedAnchor).coerceAtLeast(0L)
        savedStateHandle[KEY_ELAPSED] = elapsed
        _state.update { it.copy(elapsedMillis = elapsed) }
    }

    override fun onCleared() {
        ticker?.cancel()
        transport.stop()
    }

    private companion object {
        const val KEY_STARTED = "song_practice_started"
        const val KEY_STARTED_AT = "song_practice_started_at"
        const val KEY_PAUSED = "song_practice_paused"
        const val KEY_ELAPSED = "song_practice_elapsed"
        const val KEY_MANUAL_INDEX = "song_practice_manual_index"
        const val KEY_BPM = "song_practice_bpm"
        const val KEY_LOOP = "song_practice_loop"
        const val KEY_REVIEWING = "song_practice_reviewing"
        const val KEY_COMPLETED = "song_practice_completed"
        const val KEY_SHOW_FRETBOARD = "song_practice_show_fretboard"
        const val KEY_DIFFICULTIES = "song_practice_difficulties"
        const val KEY_RUN_ID = "song_practice_run_id"
    }
}

internal fun songPracticeActiveIndex(
    state: SongPracticeUiState,
    playback: ProgressionPlaybackUiState,
): Int = if (state.preparation?.preciseTiming == true) {
    playback.stepIndex.coerceAtLeast(0)
} else {
    state.manualStepIndex
}

internal fun encodeSongDifficulties(values: Collection<SongTransition>): String = values
    .sortedWith(compareBy<SongTransition> { it.fromChord }.thenBy { it.toChord })
    .joinToString("\n") { "${it.fromChord}\u001F${it.toChord}" }

internal fun decodeSongDifficulties(value: String): Set<SongTransition> = value.lineSequence()
    .mapNotNull { row ->
        val separator = row.indexOf('\u001F')
        if (separator <= 0 || separator >= row.lastIndex) null
        else runCatching { SongTransition(row.substring(0, separator), row.substring(separator + 1)) }.getOrNull()
    }
    .toSet()
