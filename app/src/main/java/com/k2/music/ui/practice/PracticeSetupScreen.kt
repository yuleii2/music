package com.k2.music.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeModeUi
import com.k2.music.ui.gateway.PracticeSwitchUi
import com.k2.music.ui.theme.LocalMusicMotion
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PracticeSetupRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStart: (PracticeConfigUi) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(PracticeSetupViewModel::class) { handle ->
            PracticeSetupViewModel(services.practiceGateway, handle)
        }
    }
    val viewModel: PracticeSetupViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PracticeSetupEffect.Start -> onStart(effect.config)
                is PracticeSetupEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    PracticeSetupScreen(
        state,
        onBack,
        viewModel::setMode,
        viewModel::setSymbols,
        viewModel::setDuration,
        viewModel::setBpm,
        viewModel::setTimeSignature,
        viewModel::setSwitchMode,
        viewModel::setAccent,
        viewModel::setAllowBarre,
        viewModel::setMaxFret,
        viewModel::start,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSetupScreen(
    state: PracticeSetupUiState,
    onBack: () -> Unit,
    onMode: (PracticeModeUi) -> Unit,
    onSymbols: (String) -> Unit,
    onDuration: (Int) -> Unit,
    onBpm: (Int) -> Unit,
    onTimeSignature: (String) -> Unit,
    onSwitchMode: (PracticeSwitchUi) -> Unit,
    onAccent: (Boolean) -> Unit,
    onAllowBarre: (Boolean) -> Unit,
    onMaxFret: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val config = state.config
    var advanced by rememberSaveable { mutableStateOf(false) }
    val motion = LocalMusicMotion.current
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("practice_setup_screen"),
        topBar = {
            TopAppBar(
                title = { Text("练习设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("practice_setup_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("mode") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模式", style = MaterialTheme.typography.titleMedium)
                    AdaptiveControlGroup {
                        PracticeModeUi.entries.forEach { mode ->
                            FilterChip(selected = config.mode == mode, onClick = { onMode(mode) }, label = { Text(mode.label) })
                        }
                    }
                }
            }
            item("symbols") {
                OutlinedTextField(
                    value = config.symbols,
                    onValueChange = onSymbols,
                    modifier = Modifier.fillMaxWidth().testTag("practice_symbols"),
                    label = { Text("练习和弦") },
                    supportingText = { Text(if (config.mode == PracticeModeUi.TWO_CHORD) "双和弦模式需要两个和弦" else "使用空格分隔多个和弦") },
                    minLines = 2,
                )
            }
            item("duration") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("时长：${config.durationSeconds} 秒", style = MaterialTheme.typography.titleMedium)
                    AdaptiveControlGroup {
                        listOf(30, 60).forEach { seconds ->
                            FilterChip(
                                selected = config.durationSeconds == seconds,
                                onClick = { onDuration(seconds) },
                                label = { Text("$seconds 秒") },
                            )
                        }
                        FilterChip(
                            selected = config.durationSeconds !in listOf(30, 60),
                            onClick = { onDuration(120) },
                            label = { Text("自定义") },
                        )
                    }
                    Slider(
                        value = config.durationSeconds.toFloat(),
                        onValueChange = { onDuration(it.toInt()) },
                        valueRange = 5f..600f,
                        steps = 118,
                    )
                }
            }
            item("tempo") {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${config.bpm} BPM", style = MaterialTheme.typography.titleLarge)
                        Slider(
                            value = config.bpm.toFloat(),
                            onValueChange = { onBpm(it.toInt()) },
                            valueRange = 40f..240f,
                            steps = 199,
                        )
                        AdaptiveControlGroup {
                            listOf("2/4", "3/4", "4/4", "6/8").forEach { signature ->
                                FilterChip(
                                    selected = config.timeSignature == signature,
                                    onClick = { onTimeSignature(signature) },
                                    label = { Text(signature) },
                                )
                            }
                        }
                        AdaptiveControlGroup {
                            PracticeSwitchUi.entries.forEach { mode ->
                                FilterChip(
                                    selected = config.switchMode == mode,
                                    onClick = { onSwitchMode(mode) },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                    }
                }
            }
            item("advanced_toggle") {
                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "收起高级设置" else "更多设置")
                }
            }
            item("advanced") {
                AnimatedVisibility(
                    visible = advanced,
                    enter = fadeIn(tween(motion.quick)),
                    exit = fadeOut(tween(motion.quick)),
                ) {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("首拍重音", modifier = Modifier.weight(1f))
                                Switch(checked = config.accentFirstBeat, onCheckedChange = onAccent)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("允许横按", modifier = Modifier.weight(1f))
                                Switch(checked = config.allowBarre, onCheckedChange = onAllowBarre)
                            }
                            Text("最高品位：${config.maxFret}")
                            Slider(
                                value = config.maxFret.toFloat(),
                                onValueChange = { onMaxFret(it.toInt()) },
                                valueRange = 1f..24f,
                                steps = 22,
                            )
                        }
                    }
                }
            }
            state.error?.let { item("error") { InlineMessage(it, isError = true) } }
            item("start") {
                Button(
                    onClick = onStart,
                    enabled = !state.validating,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_practice"),
                ) {
                    if (state.validating) CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始练习")
                }
            }
        }
    }
}
