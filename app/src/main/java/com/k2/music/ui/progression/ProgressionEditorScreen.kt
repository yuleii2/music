package com.k2.music.ui.progression

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.VoicingRecommendationMode
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.FretboardCanvas
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.ProgressionPlaybackUiState
import com.k2.music.ui.gateway.TransportStatus
import com.k2.music.ui.model.ProgressionPlaybackMode
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionUiModel
import com.k2.music.ui.theme.LocalMusicMotion
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
fun ProgressionEditorRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAiOptimize: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(ProgressionEditorViewModel::class) { handle ->
            ProgressionEditorViewModel(
                services.progressionGateway,
                services.progressionTransport,
                handle,
            )
        }
    }
    val viewModel: ProgressionEditorViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ProgressionEditorEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                ProgressionEditorEffect.NavigateBack -> onBack()
            }
        }
    }
    ProgressionEditorScreen(
        state = state,
        playback = playback,
        onBack = onBack,
        onName = viewModel::setName,
        onKey = viewModel::setKey,
        onBpm = viewModel::setBpm,
        onTimeSignature = viewModel::setTimeSignature,
        onLoop = viewModel::setLoop,
        onPlaybackMode = viewModel::setPlaybackMode,
        onRecommendationMode = viewModel::setRecommendationMode,
        onAllowBarre = viewModel::setAllowBarre,
        onMaxFret = viewModel::setMaxFret,
        onAddInput = viewModel::setAddInput,
        onAdd = viewModel::addSymbols,
        onSelectStep = viewModel::selectStep,
        onMoveStep = viewModel::moveStep,
        onDeleteStep = viewModel::deleteStep,
        onUpdateStep = viewModel::updateStep,
        onRecommend = viewModel::recommend,
        onSave = viewModel::save,
        onDelete = viewModel::deleteCurrent,
        onAiOptimize = { state.progression?.symbols?.let(onAiOptimize) },
        onPlayPause = viewModel::togglePlayback,
        onStop = viewModel::stopPlayback,
        onNext = viewModel::nextStep,
        onPrevious = viewModel::previousStep,
        onSeek = viewModel::seekToStep,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionEditorScreen(
    state: ProgressionEditorUiState,
    playback: ProgressionPlaybackUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onKey: (String) -> Unit,
    onBpm: (Int) -> Unit,
    onTimeSignature: (String) -> Unit,
    onLoop: (Boolean) -> Unit,
    onPlaybackMode: (ProgressionPlaybackMode) -> Unit,
    onRecommendationMode: (VoicingRecommendationMode) -> Unit,
    onAllowBarre: (Boolean) -> Unit,
    onMaxFret: (Int) -> Unit,
    onAddInput: (String) -> Unit,
    onAdd: () -> Unit,
    onSelectStep: (Int) -> Unit,
    onMoveStep: (Int, Int) -> Unit,
    onDeleteStep: (Int) -> Unit,
    onUpdateStep: (Int, Double, String, String) -> Unit,
    onRecommend: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onAiOptimize: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val progression = state.progression
    var editStep by remember { mutableStateOf<Int?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val timelineState = rememberLazyListState()
    val motion = LocalMusicMotion.current
    val activeIndex = if (playback.progressionId == progression?.id && playback.stepIndex >= 0) {
        playback.stepIndex
    } else {
        state.selectedStepIndex
    }
    LaunchedEffect(activeIndex, progression?.steps?.size, motion.allowSpatialTransitions) {
        if (progression != null && activeIndex in progression.steps.indices) {
            if (motion.allowSpatialTransitions) {
                timelineState.animateScrollToItem(activeIndex)
            } else {
                timelineState.scrollToItem(activeIndex)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("progression_editor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("进行编辑器")
                        if (progression != null) {
                            Text(
                                when {
                                    state.savingDraft -> "正在保存草稿…"
                                    state.dirty -> "草稿已自动保存"
                                    progression.saved -> "已保存"
                                    else -> "新草稿"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onAiOptimize, enabled = progression?.steps?.isNotEmpty() == true) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "用 AI 优化当前进行")
                    }
                    IconButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "显示或隐藏高级设置")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除当前进行")
                    }
                },
            )
        },
        bottomBar = {
            ProgressionTransportBar(
                progression = progression,
                playback = playback,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onStop = onStop,
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            progression == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InlineMessage(state.error ?: "无法打开和弦进行。", isError = true)
                Button(onClick = onBack) { Text("返回") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item("identity") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = progression.name,
                            onValueChange = onName,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("进行名称") },
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = progression.keySignature,
                                onValueChange = onKey,
                                modifier = Modifier.weight(1f),
                                label = { Text("调性") },
                                singleLine = true,
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${progression.bpm} BPM", style = MaterialTheme.typography.titleMedium)
                                Slider(
                                    value = progression.bpm.toFloat(),
                                    onValueChange = { onBpm(it.toInt()) },
                                    valueRange = 40f..240f,
                                    steps = 199,
                                )
                            }
                        }
                    }
                }
                state.error?.let { item("error") { InlineMessage(it, isError = true) } }
                item("timeline_title") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("时间轴", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text("长按拖动排序", style = MaterialTheme.typography.labelMedium)
                    }
                }
                item("timeline") {
                    if (progression.steps.isEmpty()) {
                        InlineMessage("进行尚无步骤，请在下方一次输入一个或多个和弦。")
                    } else {
                        LazyRow(
                            state = timelineState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth().testTag("progression_timeline"),
                        ) {
                            itemsIndexed(
                                progression.steps,
                                key = { index, step -> "$index:${step.chordSymbol}:${step.voicingId}" },
                                contentType = { _, _ -> "progression_step" },
                            ) { index, step ->
                                ProgressionStepCard(
                                    step = step,
                                    selected = index == activeIndex,
                                    playing = index == playback.stepIndex && playback.isPlaying,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < progression.steps.lastIndex,
                                    onClick = { onSeek(index) },
                                    onEdit = { editStep = index },
                                    onMove = { delta -> onMoveStep(index, delta) },
                                )
                            }
                        }
                    }
                }
                item("add") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.addInput,
                            onValueChange = onAddInput,
                            modifier = Modifier.weight(1f).testTag("progression_add_input"),
                            label = { Text("添加和弦") },
                            placeholder = { Text("C G Am F") },
                            singleLine = true,
                        )
                        IconButton(onClick = onAdd, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = "添加到进行")
                        }
                    }
                }
                progression.steps.getOrNull(activeIndex)?.let { current ->
                    item("current_voicing") {
                        CurrentStepPanel(
                            current = current,
                            next = progression.steps.getOrNull(activeIndex + 1)
                                ?: progression.steps.firstOrNull().takeIf { progression.loop && progression.steps.size > 1 },
                            onEdit = { editStep = activeIndex },
                        )
                    }
                }
                item("basic_playback") {
                    PlaybackSettings(
                        progression = progression,
                        onTimeSignature = onTimeSignature,
                        onLoop = onLoop,
                        onPlaybackMode = onPlaybackMode,
                    )
                }
                item("advanced") {
                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = fadeIn(tween(motion.quick)),
                        exit = fadeOut(tween(motion.quick)),
                    ) {
                        RecommendationSettings(
                            progression = progression,
                            onMode = onRecommendationMode,
                            onAllowBarre = onAllowBarre,
                            onMaxFret = onMaxFret,
                            onRecommend = onRecommend,
                        )
                    }
                }
                if (progression.recommendationReasons.isNotEmpty()) {
                    item("recommendation_reasons") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("推荐理由", style = MaterialTheme.typography.titleMedium)
                                progression.recommendationReasons.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                item("save") {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_progression"),
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (progression.saved) "保存更改" else "保存到本地")
                    }
                }
            }
        }
    }

    editStep?.let { index ->
        progression?.steps?.getOrNull(index)?.let { step ->
            StepEditorSheet(
                step = step,
                onDismiss = { editStep = null },
                onDelete = {
                    onDeleteStep(index)
                    editStep = null
                },
                onSave = { beats, strum, voicing ->
                    onUpdateStep(index, beats, strum, voicing)
                    editStep = null
                },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除当前进行？") },
            text = { Text(if (progression?.saved == true) "将删除本地保存的进行。" else "将放弃当前草稿。") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProgressionStepCard(
    step: ProgressionStepUi,
    selected: Boolean,
    playing: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var dragDistance by remember(step.order) { mutableFloatStateOf(0f) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(174.dp)
            .pointerInput(step.order) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragDistance = 0f },
                    onDragEnd = {
                        if (abs(dragDistance) > 48f) onMove(if (dragDistance > 0) 1 else -1)
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.x
                    },
                )
            }
            .semantics { contentDescription = "第 ${step.order + 1} 步，${step.chordSymbol}，${formatBeats(step.beats)} 拍" },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DragIndicator, contentDescription = null)
                Text(step.chordSymbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (playing) Icon(Icons.Rounded.PlayArrow, contentDescription = "正在播放")
            }
            Text("${formatBeats(step.beats)} 拍 · ${step.selectedVoicing?.name ?: "自动按法"}", style = MaterialTheme.typography.bodySmall)
            if (step.strumPattern.isNotBlank()) Text(step.strumPattern, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveLeft) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "左移 ${step.chordSymbol}")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "编辑 ${step.chordSymbol}")
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveRight) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右移 ${step.chordSymbol}")
                }
            }
        }
    }
}

@Composable
private fun CurrentStepPanel(current: ProgressionStepUi, next: ProgressionStepUi?, onEdit: () -> Unit) {
    val chord = current.chord
    val voicing = current.selectedVoicing
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前步骤", style = MaterialTheme.typography.labelLarge)
                    Text(current.chordSymbol, style = MaterialTheme.typography.headlineLarge)
                }
                TextButton(onClick = onEdit) { Text("编辑步骤") }
            }
            if (chord != null && voicing != null) {
                FretboardCanvas(
                    chord = chord,
                    voicing = voicing,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                Text(voicing.name, style = MaterialTheme.typography.titleMedium)
            } else {
                InlineMessage("当前和弦暂无收录指法，将使用组成音播放。")
            }
            next?.let {
                Text("下一步：${it.chordSymbol} · ${it.selectedVoicing?.name ?: "组成音"}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PlaybackSettings(
    progression: ProgressionUiModel,
    onTimeSignature: (String) -> Unit,
    onLoop: (Boolean) -> Unit,
    onPlaybackMode: (ProgressionPlaybackMode) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("播放设置", style = MaterialTheme.typography.titleMedium)
            AdaptiveControlGroup {
                listOf("2/4", "3/4", "4/4", "6/8").forEach { signature ->
                    FilterChip(
                        selected = progression.timeSignature == signature,
                        onClick = { onTimeSignature(signature) },
                        label = { Text(signature) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("循环播放", modifier = Modifier.weight(1f))
                Switch(checked = progression.loop, onCheckedChange = onLoop)
            }
            AdaptiveControlGroup {
                ProgressionPlaybackMode.entries.forEach { mode ->
                    FilterChip(
                        selected = progression.playbackMode == mode,
                        onClick = { onPlaybackMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationSettings(
    progression: ProgressionUiModel,
    onMode: (VoicingRecommendationMode) -> Unit,
    onAllowBarre: (Boolean) -> Unit,
    onMaxFret: (Int) -> Unit,
    onRecommend: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本地按法推荐", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(VoicingRecommendationMode.entries) { _, mode ->
                    FilterChip(
                        selected = progression.recommendationMode == mode,
                        onClick = { onMode(mode) },
                        label = { Text(recommendationModeLabel(mode)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("允许横按", modifier = Modifier.weight(1f))
                Switch(checked = progression.allowBarre, onCheckedChange = onAllowBarre)
            }
            Text("最高品位：${progression.maxFret}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = progression.maxFret.toFloat(),
                onValueChange = { onMaxFret(it.toInt()) },
                valueRange = 1f..24f,
                steps = 22,
            )
            Button(onClick = onRecommend, modifier = Modifier.fillMaxWidth()) { Text("推荐整段按法") }
        }
    }
}

@Composable
private fun ProgressionTransportBar(
    progression: ProgressionUiModel?,
    playback: ProgressionPlaybackUiState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    if (progression == null) return
    val isCurrent = playback.progressionId == progression.id
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (isCurrent) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(progression.timeSignature.substringBefore('/').toIntOrNull() ?: 4) { index ->
                        val active = playback.beatNumber == index + 1 && playback.isPlaying
                        Text(
                            if (active) "●" else "○",
                            modifier = Modifier.padding(horizontal = 5.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一个和弦") }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (isCurrent && playback.status == TransportStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isCurrent && playback.status == TransportStatus.PLAYING) "暂停" else "播放",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "下一个和弦") }
                IconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, contentDescription = "停止") }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${progression.bpm} BPM", style = MaterialTheme.typography.labelLarge)
                    Text(progression.timeSignature, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepEditorSheet(
    step: ProgressionStepUi,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Double, String, String) -> Unit,
) {
    var beats by remember(step.order) { mutableDoubleStateOf(step.beats) }
    var strum by remember(step.order) { mutableStateOf(step.strumPattern) }
    var voicingId by remember(step.order) { mutableStateOf(step.voicingId) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("title") { Text("编辑 ${step.chordSymbol}", style = MaterialTheme.typography.headlineLarge) }
            item("beats") {
                Column {
                    Text("拍数：${formatBeats(beats)}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = beats.toFloat(),
                        onValueChange = { beats = (it * 2).toInt() / 2.0 },
                        valueRange = 0.5f..16f,
                        steps = 30,
                    )
                }
            }
            item("strum") {
                OutlinedTextField(
                    value = strum,
                    onValueChange = { strum = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("扫弦型") },
                    placeholder = { Text("例如 ↓ ↓ ↑ ↑ ↓ ↑") },
                )
            }
            item("strum_presets") {
                AdaptiveControlGroup {
                    listOf("", "↓ ↓ ↑ ↑ ↓ ↑", "↓ · ↓ ↑ · ↑ ↓ ↑").forEach { pattern ->
                        FilterChip(
                            selected = strum == pattern,
                            onClick = { strum = pattern },
                            label = { Text(pattern.ifBlank { "无" }) },
                        )
                    }
                }
            }
            item("voicing_title") { Text("按法", style = MaterialTheme.typography.titleMedium) }
            if (step.voicingOptions.isEmpty()) {
                item("no_voicing") { InlineMessage("当前和弦暂无可选择按法，将播放组成音。") }
            } else {
                itemsIndexed(step.voicingOptions, key = { _, option -> option.id }) { _, option ->
                    FilterChip(
                        selected = voicingId == option.id,
                        onClick = { voicingId = option.id },
                        label = {
                            Text("${option.voicing.name} · ${option.voicing.difficulty}${if (option.voicing.barre) " · 横按" else ""}")
                        },
                    )
                }
            }
            item("actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text("删除步骤")
                    }
                    Button(
                        onClick = { onSave(beats, strum, voicingId) },
                        modifier = Modifier.weight(1f),
                    ) { Text("完成") }
                }
            }
        }
    }
}

private fun recommendationModeLabel(mode: VoicingRecommendationMode): String = when (mode) {
    VoicingRecommendationMode.AUTO -> "自动"
    VoicingRecommendationMode.BEGINNER -> "初学者"
    VoicingRecommendationMode.MINIMUM_MOVEMENT -> "最小移动"
    VoicingRecommendationMode.OPEN_CHORDS -> "开放和弦"
    VoicingRecommendationMode.HIGH_POSITION_TONE -> "高把位"
}

private fun formatBeats(value: Double): String =
    if (value == kotlin.math.floor(value)) value.toInt().toString() else value.toString()
