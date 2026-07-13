package com.k2.music.ui.gateway

import android.media.AudioManager
import android.media.ToneGenerator
import com.k2.music.ChordAudioPlayer
import com.k2.music.ChordProgression
import com.k2.music.ChordRepository
import com.k2.music.CustomVoicingStore
import com.k2.music.MetronomeEngine
import com.k2.music.PracticePreferences
import com.k2.music.ProgressionPlayer
import com.k2.music.ProgressionStep
import com.k2.music.TimeSignature
import com.k2.music.VoicingRecommendationEngine
import com.k2.music.ui.model.ProgressionPlaybackMode
import com.k2.music.ui.model.ProgressionUiModel
import com.k2.music.ui.model.toCore
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackSessionType { NONE, PROGRESSION, METRONOME }
enum class TransportStatus { STOPPED, PLAYING, PAUSED }

data class ProgressionPlaybackUiState(
    val sessionType: PlaybackSessionType = PlaybackSessionType.NONE,
    val status: TransportStatus = TransportStatus.STOPPED,
    val progressionId: String? = null,
    val title: String = "",
    val currentSymbol: String = "",
    val nextSymbol: String = "",
    val stepIndex: Int = -1,
    val measureNumber: Int = 0,
    val beatNumber: Int = 0,
    val stepBeatNumber: Int = 0,
    val bpm: Int = 80,
    val timeSignature: String = "4/4",
    val loop: Boolean = true,
    val playbackMode: ProgressionPlaybackMode = ProgressionPlaybackMode.WHOLE_CHORD,
    val beatSerial: Long = 0,
    val accentedBeat: Boolean = false,
    val beatAnchorNanos: Long = 0,
    val stepAnchorNanos: Long = 0,
    val error: String? = null,
) {
    val isVisible: Boolean get() = sessionType != PlaybackSessionType.NONE && status != TransportStatus.STOPPED
    val isPlaying: Boolean get() = status == TransportStatus.PLAYING
}

interface ProgressionTransport {
    val state: StateFlow<ProgressionPlaybackUiState>
    fun play(progression: ProgressionUiModel)
    fun toggle()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    fun seekToStep(index: Int)
    fun updateBpm(value: Int)
    fun updateLoop(value: Boolean)
    fun updatePlaybackMode(value: ProgressionPlaybackMode)
    fun startMetronome(bpm: Int, timeSignature: String, accentFirstBeat: Boolean = true)
    fun pauseForLifecycle()
}

class DefaultProgressionTransport(
    private val repository: ChordRepository,
    private val customVoicingStore: CustomVoicingStore,
    private val audioPlayer: ChordAudioPlayer,
    private val beforeStart: () -> Unit = {},
) : ProgressionTransport, Closeable {
    private val _state = MutableStateFlow(ProgressionPlaybackUiState())
    private var activeProgression: ChordProgression? = null
    private var activeMode = ProgressionPlaybackMode.WHOLE_CHORD
    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    }.getOrNull()

    private val player = ProgressionPlayer(object : ProgressionPlayer.AudioOutput {
        override fun play(step: ProgressionStep, mode: PracticePreferences.PlaybackMode) {
            playStepAudio(step, mode)
        }

        override fun stop() {
            audioPlayer.stop()
        }
    })
    private val metronome = MetronomeEngine { accented ->
        toneGenerator?.startTone(
            if (accented) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
            if (accented) 42 else 32,
        )
    }

    override val state: StateFlow<ProgressionPlaybackUiState> = _state.asStateFlow()

    init {
        player.setListener(object : ProgressionPlayer.Listener {
            override fun onStateChanged(state: ProgressionPlayer.State) {
                if (_state.value.sessionType != PlaybackSessionType.PROGRESSION) return
                val status = state.toUiStatus()
                if (status == TransportStatus.STOPPED && metronome.state() != MetronomeEngine.State.RELEASED) {
                    runCatching { metronome.stop() }
                }
                _state.update { it.copy(status = status) }
            }

            override fun onPositionChanged(position: ProgressionPlayer.Position) {
                if (_state.value.sessionType != PlaybackSessionType.PROGRESSION) return
                _state.update {
                    val changedStep = position.stepIndex >= 0 && position.stepIndex != it.stepIndex
                    it.copy(
                        status = position.state.toUiStatus(),
                        currentSymbol = position.currentStep?.chordSymbol.orEmpty(),
                        nextSymbol = position.nextStep?.chordSymbol.orEmpty(),
                        stepIndex = position.stepIndex,
                        measureNumber = position.measureNumber,
                        beatNumber = position.beatNumber,
                        stepBeatNumber = position.stepBeatNumber,
                        stepAnchorNanos = if (changedStep) System.nanoTime() else it.stepAnchorNanos,
                    )
                }
            }

            override fun onError(error: RuntimeException) {
                _state.update { it.copy(error = error.message ?: "和弦进行播放失败。") }
            }
        })
        metronome.setListener(object : MetronomeEngine.Listener {
            override fun onTick(beatNumber: Int, accented: Boolean, scheduledTimeNanos: Long) {
                _state.update {
                    it.copy(
                        beatNumber = beatNumber,
                        beatSerial = it.beatSerial + 1,
                        accentedBeat = accented,
                        beatAnchorNanos = scheduledTimeNanos,
                    )
                }
            }

            override fun onStateChanged(state: MetronomeEngine.State) {
                if (_state.value.sessionType != PlaybackSessionType.METRONOME) return
                _state.update {
                    it.copy(
                        status = when (state) {
                            MetronomeEngine.State.RUNNING -> TransportStatus.PLAYING
                            MetronomeEngine.State.PAUSED -> TransportStatus.PAUSED
                            else -> TransportStatus.STOPPED
                        },
                    )
                }
            }

            override fun onError(error: RuntimeException) {
                _state.update { it.copy(error = error.message ?: "节拍器播放失败。") }
            }
        })
    }

    override fun play(progression: ProgressionUiModel) {
        beforeStart()
        val core = progression.toCore()
        val isSame = activeProgression?.id == core.id
        if (!isSame || player.state() == ProgressionPlayer.State.STOPPED) {
            player.setProgression(core)
        }
        activeProgression = core
        activeMode = progression.playbackMode
        player.setPlaybackMode(progression.playbackMode.toCore())
        player.setBpm(progression.bpm)
        player.setLoop(progression.loop)
        metronome.stop()
        metronome.setBpm(progression.bpm)
        metronome.setTimeSignature(TimeSignature.parse(progression.timeSignature))
        metronome.setAccentFirstBeat(true)
        _state.value = ProgressionPlaybackUiState(
            sessionType = PlaybackSessionType.PROGRESSION,
            status = TransportStatus.STOPPED,
            progressionId = progression.id,
            title = progression.name,
            currentSymbol = progression.steps.firstOrNull()?.chordSymbol.orEmpty(),
            nextSymbol = progression.steps.getOrNull(1)?.chordSymbol.orEmpty(),
            stepIndex = if (progression.steps.isEmpty()) -1 else 0,
            bpm = progression.bpm,
            timeSignature = progression.timeSignature,
            loop = progression.loop,
            playbackMode = progression.playbackMode,
        )
        require(progression.steps.isNotEmpty()) { "进行至少需要一个和弦才能播放。" }
        val anchor = System.nanoTime() + 100_000_000L
        player.playAt(anchor)
        metronome.startAt(anchor)
    }

    override fun toggle() {
        when (_state.value.sessionType) {
            PlaybackSessionType.PROGRESSION -> when (player.state()) {
                ProgressionPlayer.State.PLAYING -> pause()
                ProgressionPlayer.State.PAUSED -> resumeProgression()
                ProgressionPlayer.State.STOPPED -> activeProgression?.let { core ->
                    val ui = _state.value
                    beforeStart()
                    player.setProgression(core)
                    player.setPlaybackMode(activeMode.toCore())
                    val anchor = System.nanoTime() + 100_000_000L
                    player.playAt(anchor)
                    metronome.setBpm(ui.bpm)
                    metronome.setTimeSignature(TimeSignature.parse(ui.timeSignature))
                    metronome.startAt(anchor)
                }
                ProgressionPlayer.State.RELEASED -> Unit
            }
            PlaybackSessionType.METRONOME -> when (metronome.state()) {
                MetronomeEngine.State.RUNNING -> pause()
                MetronomeEngine.State.PAUSED -> {
                    val anchor = System.nanoTime() + 60_000_000L
                    metronome.startAt(anchor)
                }
                MetronomeEngine.State.STOPPED -> startMetronome(
                    _state.value.bpm,
                    _state.value.timeSignature,
                )
                MetronomeEngine.State.RELEASED -> Unit
            }
            PlaybackSessionType.NONE -> Unit
        }
    }

    override fun pause() {
        when (_state.value.sessionType) {
            PlaybackSessionType.PROGRESSION -> {
                player.pause()
                metronome.pause()
            }
            PlaybackSessionType.METRONOME -> metronome.pause()
            PlaybackSessionType.NONE -> Unit
        }
        audioPlayer.stop()
    }

    override fun stop() {
        runCatching { player.stop() }
        runCatching { metronome.stop() }
        audioPlayer.stop()
        _state.update { it.copy(status = TransportStatus.STOPPED, stepIndex = if (it.sessionType == PlaybackSessionType.PROGRESSION) 0 else -1) }
    }

    override fun next() {
        if (_state.value.sessionType == PlaybackSessionType.PROGRESSION) player.next()
    }

    override fun previous() {
        if (_state.value.sessionType == PlaybackSessionType.PROGRESSION) player.previous()
    }

    override fun seekToStep(index: Int) {
        if (_state.value.sessionType == PlaybackSessionType.PROGRESSION) player.seekToStep(index)
    }

    override fun updateBpm(value: Int) {
        val safe = value.coerceIn(40, 240)
        player.setBpm(safe)
        metronome.setBpm(safe)
        _state.update { it.copy(bpm = safe) }
    }

    override fun updateLoop(value: Boolean) {
        player.setLoop(value)
        _state.update { it.copy(loop = value) }
    }

    override fun updatePlaybackMode(value: ProgressionPlaybackMode) {
        activeMode = value
        player.setPlaybackMode(value.toCore())
        _state.update { it.copy(playbackMode = value) }
    }

    override fun startMetronome(bpm: Int, timeSignature: String, accentFirstBeat: Boolean) {
        beforeStart()
        runCatching { player.stop() }
        audioPlayer.stop()
        val safeBpm = bpm.coerceIn(40, 240)
        val signature = TimeSignature.parse(timeSignature)
        metronome.stop()
        metronome.setBpm(safeBpm)
        metronome.setTimeSignature(signature)
        metronome.setAccentFirstBeat(accentFirstBeat)
        _state.value = ProgressionPlaybackUiState(
            sessionType = PlaybackSessionType.METRONOME,
            status = TransportStatus.STOPPED,
            title = "节拍器",
            bpm = safeBpm,
            timeSignature = signature.toString(),
            beatNumber = 1,
        )
        metronome.startAt(System.nanoTime() + 60_000_000L)
    }

    override fun pauseForLifecycle() {
        pause()
    }

    override fun close() {
        runCatching { player.close() }
        runCatching { metronome.close() }
        toneGenerator?.release()
        audioPlayer.stop()
    }

    private fun resumeProgression() {
        beforeStart()
        val anchor = System.nanoTime() + 100_000_000L
        player.playAt(anchor)
        metronome.startAt(anchor)
    }

    private fun playStepAudio(step: ProgressionStep, mode: PracticePreferences.PlaybackMode) {
        val lookup = repository.find(step.chordSymbol)
        if (!lookup.recognized || lookup.chord == null) return
        val candidates = customVoicingStore.mergeWithBuiltIns(step.chordSymbol, lookup.chord.voicings)
        val selected = candidates.firstOrNull {
            VoicingRecommendationEngine.voicingId(step.chordSymbol, it) == step.voicingId
        } ?: candidates.firstOrNull()
        val notes = selected?.playableMidiNotes() ?: lookup.chord.fallbackMidiNotes()
        if (mode == PracticePreferences.PlaybackMode.ARPEGGIO) {
            audioPlayer.playArpeggio(notes)
        } else {
            audioPlayer.play(notes)
        }
    }

    private fun ProgressionPlayer.State.toUiStatus(): TransportStatus = when (this) {
        ProgressionPlayer.State.PLAYING -> TransportStatus.PLAYING
        ProgressionPlayer.State.PAUSED -> TransportStatus.PAUSED
        else -> TransportStatus.STOPPED
    }
}
