package com.k2.music.ui.song

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.k2.music.ui.components.StudioOutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.k2.music.ui.components.StudioTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.components.FretboardCanvas
import com.k2.music.ui.components.InlineMessage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SongPracticeRoute(
    services: CoreServices,
    onBack: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(SongPracticeViewModel::class) { handle ->
            SongPracticeViewModel(services.songGateway, services.progressionTransport, handle)
        }
    }
    val viewModel: SongPracticeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { onBack() }
    }
    SongPracticeScreen(
        state = state,
        playback = playback,
        onBack = onBack,
        onStartPause = viewModel::startOrTogglePause,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onLoop = viewModel::setLoop,
        onBpm = viewModel::adjustBpm,
        onToggleFretboard = viewModel::toggleFretboard,
        onFinish = viewModel::finishForReview,
        onCompleted = viewModel::toggleCompleted,
        onDifficulty = viewModel::toggleDifficulty,
        onSave = viewModel::saveReview,
        onAbandon = viewModel::abandon,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPracticeScreen(
    state: SongPracticeUiState,
    playback: com.k2.music.ui.gateway.ProgressionPlaybackUiState,
    onBack: () -> Unit,
    onStartPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLoop: (Boolean) -> Unit,
    onBpm: (Int) -> Unit,
    onToggleFretboard: () -> Unit,
    onFinish: () -> Unit,
    onCompleted: () -> Unit,
    onDifficulty: (com.k2.music.song.SongTransition) -> Unit,
    onSave: () -> Unit,
    onAbandon: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = state.savedRun == null) { confirmExit = true }
    val preparation = state.preparation
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("song_practice_screen"),
        topBar = {
            StudioTopAppBar(
                title = {
                    Column {
                        Text(preparation?.project?.title ?: "连续演奏")
                        preparation?.let {
                            Text(
                                it.sectionName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.semantics { contentDescription = "当前段落：${it.sectionName}" },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.savedRun != null) onBack() else confirmExit = true }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            preparation == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) { InlineMessage(state.error ?: "无法读取曲谱练习。", isError = true) }
            state.savedRun != null -> SongPerformanceSaved(
                state,
                Modifier.padding(padding),
                onBack,
            )
            state.reviewing -> SongPerformanceReview(
                state,
                Modifier.padding(padding),
                onCompleted,
                onDifficulty,
                onSave,
            )
            else -> SongPerformanceActive(
                state,
                playback,
                Modifier.padding(padding),
                onStartPause,
                onPrevious,
                onNext,
                onLoop,
                onBpm,
                onToggleFretboard,
                onFinish,
            )
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("离开连续演奏？") },
            text = { Text("“结束并总结”会让你选择完成状态和困难切换；“放弃”不会生成练习记录。") },
            confirmButton = {
                StudioButton(
                    onClick = { confirmExit = false; onFinish() },
                    enabled = state.started,
                ) { Text("结束并总结") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; onAbandon() }) { Text("放弃") }
                    TextButton(onClick = { confirmExit = false }) { Text("继续") }
                }
            },
        )
    }
}

@Composable
private fun SongPerformanceActive(
    state: SongPracticeUiState,
    playback: com.k2.music.ui.gateway.ProgressionPlaybackUiState,
    modifier: Modifier,
    onStartPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLoop: (Boolean) -> Unit,
    onBpm: (Int) -> Unit,
    onToggleFretboard: () -> Unit,
    onFinish: () -> Unit,
) {
    val preparation = requireNotNull(state.preparation)
    val activeIndex = songPracticeActiveIndex(state, playback).coerceIn(preparation.progression.steps.indices)
    val current = preparation.progression.steps[activeIndex]
    val next = preparation.progression.steps.getOrNull(activeIndex + 1)
        ?: preparation.progression.steps.firstOrNull().takeIf { state.loop }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InlineMessage(preparation.timingMessage, isError = !preparation.preciseTiming)
        state.error?.let { InlineMessage(it, isError = true) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("真实练习时间")
                Text(formatSongTimer(state.elapsedMillis), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onStartPause, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (state.paused) "开始或继续连续演奏" else "暂停连续演奏",
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onFinish, enabled = state.started) {
                Icon(Icons.Rounded.Close, contentDescription = "结束连续演奏")
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前和弦", style = MaterialTheme.typography.labelLarge)
                Text(
                    current.chordSymbol,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("song_current_chord")
                        .semantics { contentDescription = "当前和弦：${current.chordSymbol}" },
                )
                Text(
                    "下一和弦：${next?.chordSymbol ?: "结束"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .testTag("song_next_chord")
                        .semantics { contentDescription = "下一和弦：${next?.chordSymbol ?: "结束"}" },
                )
                Text(
                    preparation.lyricLines.getOrNull(activeIndex).orEmpty().ifBlank { "当前行没有歌词" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    if (preparation.preciseTiming) {
                        "第 ${playback.measureNumber.coerceAtLeast(1)} 小节 · 第 ${playback.beatNumber.coerceAtLeast(1)} 拍"
                    } else {
                        "手动步骤 ${activeIndex + 1} / ${preparation.progression.steps.size}"
                    },
                )
            }
        }
        if (state.showFretboard) {
            val chord = current.chord
            val voicing = current.selectedVoicing
            if (chord != null && voicing != null) {
                FretboardCanvas(chord, voicing, Modifier.fillMaxWidth().height(300.dp))
            } else {
                InlineMessage("当前手型暂无可用指板图。")
            }
        }
        StudioOutlinedButton(onClick = onToggleFretboard, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.showFretboard) "隐藏指板图" else "显示指板图")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一个和弦") }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "下一个和弦") }
            Spacer(Modifier.weight(1f))
            Text("循环")
            Switch(checked = state.loop, onCheckedChange = onLoop)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("速度", Modifier.weight(1f))
            TextButton(onClick = { onBpm(-5) }) { Text("−5") }
            Text("${state.currentBpm} BPM", fontWeight = FontWeight.Bold)
            TextButton(onClick = { onBpm(5) }) { Text("+5") }
        }
        StudioButton(onClick = onFinish, enabled = state.started, modifier = Modifier.fillMaxWidth()) { Text("结束并总结") }
    }
}

@Composable
private fun SongPerformanceReview(
    state: SongPracticeUiState,
    modifier: Modifier,
    onCompleted: () -> Unit,
    onDifficulty: (com.k2.music.song.SongTransition) -> Unit,
    onSave: () -> Unit,
) {
    val preparation = requireNotNull(state.preparation)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("演奏总结", style = MaterialTheme.typography.headlineLarge)
        Text("只记录真实时长、完成情况、BPM 与配置；不会自动生成成功率。")
        AdaptiveStatGrid(
            listOf(
                AdaptiveStat("实际时长", formatSongTimer(state.elapsedMillis)),
                AdaptiveStat("速度", "${state.currentBpm} BPM"),
                AdaptiveStat("出现切换", preparation.transitions.size.toString()),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.completed,
                onCheckedChange = { onCompleted() },
                modifier = Modifier.testTag("song_completed_checkbox"),
            )
            Text("我完整演奏了所选范围")
        }
        Text("手动勾选困难切换", style = MaterialTheme.typography.titleLarge)
        Text("未勾选的切换不会被视为成功；勾选项也不会写成 TransitionAttempt 失败。")
        preparation.transitions.forEachIndexed { index, transition ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = transition in state.selectedDifficulties,
                    onCheckedChange = { onDifficulty(transition) },
                    modifier = Modifier.testTag("song_difficulty_checkbox_$index"),
                )
                Text("${transition.fromChord} → ${transition.toChord}")
            }
        }
        state.error?.let { InlineMessage(it, isError = true) }
        StudioButton(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("save_song_performance")) {
            Text(if (state.saving) "正在保存…" else "保存演奏记录")
        }
    }
}

@Composable
private fun SongPerformanceSaved(
    state: SongPracticeUiState,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    val run = requireNotNull(state.savedRun)
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("连续演奏已保存", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(18.dp))
        AdaptiveStatGrid(
            listOf(
                AdaptiveStat("实际时长", "${run.actualDurationSeconds} 秒"),
                AdaptiveStat("速度", "${run.bpm} BPM"),
                AdaptiveStat("完成状态", if (run.completed) "已完成" else "未完整演奏"),
                AdaptiveStat("困难切换", run.reportedDifficultTransitions.size.toString()),
            ),
        )
        Text("本次没有自动生成成功率，也没有把未勾选切换记为成功。", modifier = Modifier.padding(vertical = 16.dp))
        StudioButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("返回曲谱详情") }
    }
}

private fun formatSongTimer(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
