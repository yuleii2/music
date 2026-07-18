package com.k2.music.ui.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.k2.music.ui.components.StudioTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.ProgressionTransport
import com.k2.music.ui.gateway.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MetronomeUiState(
    val bpm: Int = 80,
    val timeSignature: String = "4/4",
    val accentFirstBeat: Boolean = true,
)

class MetronomeViewModel(
    private val transport: ProgressionTransport,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MetronomeUiState(
            bpm = savedStateHandle["metronome_bpm"] ?: 80,
            timeSignature = savedStateHandle["metronome_signature"] ?: "4/4",
            accentFirstBeat = savedStateHandle["metronome_accent"] ?: true,
        ),
    )
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()
    val playback = transport.state

    fun setBpm(value: Int) {
        val safe = value.coerceIn(40, 240)
        savedStateHandle["metronome_bpm"] = safe
        _state.value = _state.value.copy(bpm = safe)
        if (transport.state.value.sessionType == PlaybackSessionType.METRONOME) transport.updateBpm(safe)
    }

    fun setTimeSignature(value: String) {
        savedStateHandle["metronome_signature"] = value
        _state.value = _state.value.copy(timeSignature = value)
        if (transport.state.value.sessionType == PlaybackSessionType.METRONOME && transport.state.value.isPlaying) {
            start()
        }
    }

    fun setAccent(value: Boolean) {
        savedStateHandle["metronome_accent"] = value
        _state.value = _state.value.copy(accentFirstBeat = value)
        if (transport.state.value.sessionType == PlaybackSessionType.METRONOME && transport.state.value.isPlaying) {
            start()
        }
    }

    fun toggle() {
        if (
            transport.state.value.sessionType == PlaybackSessionType.METRONOME &&
            transport.state.value.status != TransportStatus.STOPPED
        ) {
            transport.toggle()
        } else {
            start()
        }
    }

    fun stop() = transport.stop()

    private fun start() {
        val value = _state.value
        transport.startMetronome(value.bpm, value.timeSignature, value.accentFirstBeat)
    }
}

@Composable
fun MetronomeRoute(services: CoreServices, onBack: () -> Unit) {
    val factory = remember(services) {
        MusicViewModelFactory(MetronomeViewModel::class) { handle ->
            MetronomeViewModel(services.progressionTransport, handle)
        }
    }
    val viewModel: MetronomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    MetronomeScreen(
        state = state,
        beatNumber = if (playback.sessionType == PlaybackSessionType.METRONOME) playback.beatNumber else 0,
        isPlaying = playback.sessionType == PlaybackSessionType.METRONOME && playback.isPlaying,
        onBack = onBack,
        onBpm = viewModel::setBpm,
        onSignature = viewModel::setTimeSignature,
        onAccent = viewModel::setAccent,
        onToggle = viewModel::toggle,
        onStop = viewModel::stop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    beatNumber: Int,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onBpm: (Int) -> Unit,
    onSignature: (String) -> Unit,
    onAccent: (Boolean) -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("metronome_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text("节拍器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("${state.bpm}", style = MaterialTheme.typography.displayLarge)
                    Text("BPM", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = state.bpm.toFloat(),
                        onValueChange = { onBpm(it.toInt()) },
                        valueRange = 40f..240f,
                        steps = 199,
                    )
                    AdaptiveControlGroup {
                        listOf("2/4", "3/4", "4/4", "6/8").forEach { signature ->
                            FilterChip(
                                selected = state.timeSignature == signature,
                                onClick = { onSignature(signature) },
                                label = { Text(signature) },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("首拍重音", modifier = Modifier.weight(1f))
                        Switch(checked = state.accentFirstBeat, onCheckedChange = onAccent)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(state.timeSignature.substringBefore('/').toIntOrNull() ?: 4) { index ->
                            Text(
                                if (isPlaying && beatNumber == index + 1) "●" else "○",
                                style = MaterialTheme.typography.headlineLarge,
                                color = if (isPlaying && beatNumber == index + 1) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StudioButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "暂停节拍器" else "开始节拍器",
                            )
                        }
                        IconButton(onClick = onStop, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Rounded.Stop, contentDescription = "停止节拍器")
                        }
                    }
                }
            }
        }
    }
}
