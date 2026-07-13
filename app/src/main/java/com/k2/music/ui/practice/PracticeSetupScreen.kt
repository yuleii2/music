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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import com.k2.music.ui.preferences.LocalExperienceCapabilities
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
            PracticeSetupViewModel(
                services.practiceGateway,
                handle,
                services.userLibraryGateway,
                services.progressionGateway,
                services.practicePreferencesStore,
            )
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
        viewModel::addSymbol,
        viewModel::removeSymbol,
        viewModel::useProgression,
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
    onAddSymbol: (String) -> Unit,
    onRemoveSymbol: (String) -> Unit,
    onUseProgression: (PracticeProgressionChoice) -> Unit,
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
    val capabilities = LocalExperienceCapabilities.current
    var advanced by rememberSaveable(capabilities.expandAdvancedPracticeSettings) {
        mutableStateOf(capabilities.expandAdvancedPracticeSettings)
    }
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
            item("presets") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("练习预设", style = MaterialTheme.typography.titleMedium)
                    AdaptiveControlGroup {
                        FilterChip(
                            selected = config.symbols == "C G" && config.bpm == 50 && config.durationSeconds == 60,
                            onClick = {
                                onMode(PracticeModeUi.TWO_CHORD); onSymbols("C G"); onBpm(50)
                                onDuration(60); onSwitchMode(PracticeSwitchUi.EACH_MEASURE)
                            },
                            label = { Text("第一次换和弦") },
                        )
                        FilterChip(
                            selected = config.bpm == 60 && config.durationSeconds == 120,
                            onClick = {
                                onMode(PracticeModeUi.MULTI_CHORD); onBpm(60); onDuration(120)
                                onSwitchMode(PracticeSwitchUi.EACH_MEASURE)
                            },
                            label = { Text("基础流畅度") },
                        )
                        if (state.familiarSymbols.size >= 2) {
                            val familiar = state.familiarSymbols.take(4).joinToString(" ")
                            FilterChip(
                                selected = config.bpm >= 70 && config.mode == PracticeModeUi.RANDOM && config.symbols == familiar,
                                onClick = {
                                    onMode(PracticeModeUi.RANDOM); onSymbols(familiar)
                                    onBpm(maxOf(70, config.bpm + 5)); onDuration(120)
                                },
                                label = { Text("速度挑战") },
                            )
                        }
                        state.progressions.firstOrNull()?.let { progression ->
                            FilterChip(
                                selected = config.sourceProgressionId == progression.id && config.useProgressionRhythm,
                                onClick = {
                                    onUseProgression(progression); onBpm(70); onDuration(120)
                                },
                                label = { Text("弹唱准备") },
                            )
                        }
                        state.weakTransitionSymbols?.let { symbols ->
                            FilterChip(
                                selected = config.mode == PracticeModeUi.TWO_CHORD && config.symbols == symbols,
                                onClick = {
                                    onMode(PracticeModeUi.TWO_CHORD); onSymbols(symbols)
                                    onBpm((config.bpm - 5).coerceAtLeast(40)); onDuration(120)
                                    onSwitchMode(PracticeSwitchUi.EACH_MEASURE)
                                },
                                label = { Text("薄弱项复习") },
                            )
                        }
                    }
                }
            }
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("选择练习和弦", style = MaterialTheme.typography.titleMedium)
                    val selected = config.symbols.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (selected.isNotEmpty()) {
                        AdaptiveControlGroup {
                            selected.forEach { symbol ->
                                FilterChip(
                                    selected = true,
                                    onClick = { onRemoveSymbol(symbol) },
                                    label = { Text("$symbol  ×") },
                                )
                            }
                        }
                    }
                    SymbolChoiceSection("最近查看", state.recentSymbols, onAddSymbol)
                    SymbolChoiceSection("收藏", state.favoriteSymbols, onAddSymbol)
                    SymbolChoiceSection("已熟悉", state.familiarSymbols, onAddSymbol)
                    SymbolChoiceSection("推荐", state.recommendedSymbols, onAddSymbol)
                    if (state.progressions.isNotEmpty()) {
                        Text("从保存的进行选择", style = MaterialTheme.typography.labelLarge)
                        AdaptiveControlGroup {
                            state.progressions.forEach { progression ->
                                FilterChip(
                                    selected = config.sourceProgressionId == progression.id,
                                    onClick = { onUseProgression(progression) },
                                    label = { Text(progression.name) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = config.symbols,
                        onValueChange = onSymbols,
                        modifier = Modifier.fillMaxWidth().testTag("practice_symbols"),
                        label = { Text(if (capabilities.showTechnicalLabels) "文本快速输入" else "搜索或手动输入") },
                        supportingText = { Text(if (config.mode == PracticeModeUi.TWO_CHORD) "双和弦模式需要两个和弦" else "使用空格分隔多个和弦") },
                        minLines = if (capabilities.showTechnicalLabels) 1 else 2,
                    )
                }
            }
            item("duration") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("时长：${config.durationSeconds} 秒", style = MaterialTheme.typography.titleMedium)
                    AdaptiveControlGroup {
                        listOf(30, 60, 120, 300).forEach { seconds ->
                            FilterChip(
                                selected = config.durationSeconds == seconds,
                                onClick = { onDuration(seconds) },
                                label = { Text("$seconds 秒") },
                            )
                        }
                        FilterChip(
                            selected = config.durationSeconds !in listOf(30, 60, 120, 300),
                            onClick = { onDuration(120) },
                            label = { Text("自定义") },
                        )
                    }
                    if (capabilities.showTechnicalLabels) {
                        Slider(
                            value = config.durationSeconds.toFloat(),
                            onValueChange = { onDuration(it.toInt()) },
                            valueRange = 5f..600f,
                            steps = 118,
                        )
                    }
                }
            }
            item("tempo") {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${config.bpm} BPM", style = MaterialTheme.typography.titleLarge)
                        AdaptiveControlGroup {
                            listOf(-5, -1, 1, 5).forEach { delta ->
                                TextButton(onClick = { onBpm(config.bpm + delta) }) {
                                    Text(if (delta > 0) "+$delta" else "$delta")
                                }
                            }
                            listOf(40, 50, 60, 70, 80, 100).forEach { value ->
                                FilterChip(
                                    selected = config.bpm == value,
                                    onClick = { onBpm(value) },
                                    label = { Text(value.toString()) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = config.bpm.toString(),
                            onValueChange = { it.toIntOrNull()?.let(onBpm) },
                            label = { Text("输入 BPM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                        if (config.useProgressionRhythm) {
                            Text(
                                "当前沿用保存进行的每步节奏；点选下方切换方式会改为统一节奏。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
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
            item("summary") {
                InlineMessage(
                    "练习 ${config.symbols.ifBlank { "所选和弦" }}，" +
                        "${if (config.useProgressionRhythm) "按保存的进行节奏循环" else config.switchMode.label}，" +
                        "${config.bpm} BPM，持续 ${if (config.durationSeconds % 60 == 0) "${config.durationSeconds / 60} 分钟" else "${config.durationSeconds} 秒"}。",
                )
            }
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

@Composable
private fun SymbolChoiceSection(title: String, symbols: List<String>, onAdd: (String) -> Unit) {
    if (symbols.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge)
    AdaptiveControlGroup {
        symbols.distinct().take(10).forEach { symbol ->
            FilterChip(selected = false, onClick = { onAdd(symbol) }, label = { Text(symbol) })
        }
    }
}
