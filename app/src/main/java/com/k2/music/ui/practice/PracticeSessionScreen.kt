package com.k2.music.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.FretboardCanvas
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeResultUi
import com.k2.music.ui.gateway.ProgressionPlaybackUiState
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.theme.LocalMusicMotion
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PracticeSessionRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onFinished: (PracticeResultUi, PracticeConfigUi) -> Unit,
    onAbandon: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(PracticeSessionViewModel::class) { handle ->
            PracticeSessionViewModel(services.practiceGateway, services.progressionTransport, handle)
        }
    }
    val viewModel: PracticeSessionViewModel = viewModel(factory = factory)
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
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PracticeSessionEffect.Finished -> onFinished(effect.result, effect.config)
                is PracticeSessionEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    PracticeSessionScreen(
        state = state,
        playback = playback,
        onPauseResume = viewModel::togglePause,
        onSuccess = viewModel::recordSuccess,
        onFailure = viewModel::recordFailure,
        onReset = viewModel::reset,
        onFinish = viewModel::finish,
        onAbandon = {
            viewModel.abandon()
            onAbandon()
        },
    )
}

@Composable
fun PracticeSessionScreen(
    state: PracticeSessionUiState,
    playback: ProgressionPlaybackUiState,
    onPauseResume: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    onReset: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    val motion = LocalMusicMotion.current
    val beatScale = remember { Animatable(1f) }
    LaunchedEffect(playback.beatSerial) {
        if (playback.beatSerial > 0 && playback.isPlaying) {
            if (motion.quick > 0) {
                beatScale.snapTo(if (playback.accentedBeat) 1.22f else 1.10f)
                beatScale.animateTo(1f, tween(motion.quick))
            } else {
                beatScale.snapTo(1f)
            }
        }
    }
    val progression = state.progression
    val activeIndex = playback.stepIndex.takeIf { it >= 0 } ?: 0
    val current = progression?.steps?.getOrNull(activeIndex) ?: progression?.steps?.firstOrNull()
    val next = progression?.steps?.getOrNull(activeIndex + 1)
        ?: progression?.steps?.firstOrNull().takeIf { progression?.steps?.size.orZero() > 1 }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag("practice_session_screen").padding(18.dp),
    ) {
        val useWideLayout = maxWidth >= 700.dp
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("剩余时间", style = MaterialTheme.typography.labelLarge)
                        Text(formatTimer(state.remainingMillis), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onPauseResume, modifier = Modifier.size(56.dp)) {
                        Icon(
                            if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (state.paused) "继续练习" else "暂停练习",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    IconButton(onClick = { confirmExit = true }) {
                        Icon(Icons.Rounded.Close, contentDescription = "结束并离开练习")
                    }
                }
                state.error?.let { InlineMessage(it, isError = true) }
                if (useWideLayout) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PracticeChordPanel(current, next, Modifier.weight(1.4f))
                        PracticeStatusPanel(state, playback, beatScale.value, onSuccess, onFailure, onReset, onFinish, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PracticeChordPanel(current, next)
                        PracticeStatusPanel(state, playback, beatScale.value, onSuccess, onFailure, onReset, onFinish)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("结束练习？") },
            text = { Text("可以继续练习，或结束并保存当前可见总结。") },
            confirmButton = {
                Button(onClick = {
                    confirmExit = false
                    onFinish()
                }) { Text("结束并保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmExit = false
                        onAbandon()
                    }) { Text("放弃") }
                    TextButton(onClick = { confirmExit = false }) { Text("继续") }
                }
            },
        )
    }
}

@Composable
private fun PracticeChordPanel(current: ProgressionStepUi?, next: ProgressionStepUi?, modifier: Modifier = Modifier) {
    val motion = LocalMusicMotion.current
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = current?.chordSymbol.orEmpty(),
                transitionSpec = {
                    fadeIn(tween(motion.quick)) togetherWith fadeOut(tween(motion.quick))
                },
                label = "practice_chord",
            ) { symbol ->
                Text(symbol.ifBlank { "准备中" }, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            }
            val chord = current?.chord
            val voicing = current?.selectedVoicing
            if (chord != null && voicing != null) {
                FretboardCanvas(
                    chord,
                    voicing,
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp),
                )
            } else {
                Text("暂无吉他按法，将播放和弦组成音", style = MaterialTheme.typography.bodyMedium)
            }
            Text("下一和弦：${next?.chordSymbol ?: "结束"}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PracticeStatusPanel(
    state: PracticeSessionUiState,
    playback: ProgressionPlaybackUiState,
    beatScale: Float,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    onReset: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (playback.accentedBeat) "●" else "•",
                    modifier = Modifier.scale(beatScale),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text("第 ${playback.measureNumber.coerceAtLeast(1)} 小节 · 第 ${playback.beatNumber.coerceAtLeast(1)} 拍")
            }
        }
        AdaptiveStatGrid(
            listOf(
                AdaptiveStat("尝试", state.completionCount.toString()),
                AdaptiveStat("成功", state.successCount.toString()),
                AdaptiveStat("失败", state.failureCount.toString()),
                AdaptiveStat("当前连续", state.currentStreak.toString()),
                AdaptiveStat("最佳连续", state.bestStreak.toString()),
            ),
            modifier = Modifier.testTag("practice_stats").semantics {
                contentDescription = "尝试 ${state.completionCount}，成功 ${state.successCount}，失败 ${state.failureCount}"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSuccess,
                enabled = !state.paused && !state.finishing && !state.recordingResult,
                modifier = Modifier.weight(1f).height(68.dp).testTag("practice_success"),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("跟上了")
            }
            OutlinedButton(
                onClick = onFailure,
                enabled = !state.paused && !state.finishing && !state.recordingResult,
                modifier = Modifier.weight(1f).height(68.dp).testTag("practice_failure"),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("没跟上")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Replay, contentDescription = null)
                Text("重置")
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f).testTag("finish_practice"),
                enabled = !state.finishing,
            ) {
                Text(if (state.finishing) "保存中…" else "完成")
            }
        }
    }
}

@Composable
fun PracticeResultScreen(
    seconds: Int,
    attempts: Int,
    successes: Int,
    failures: Int,
    streak: Int,
    symbols: String,
    previousRate: Double?,
    hardestTransition: String?,
    suggestedBpm: Int,
    suggestionReason: String,
    onAgain: () -> Unit,
    onSuggestedAgain: () -> Unit,
    onAdjust: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("practice_result_screen").padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("练习完成", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("实际时长：${seconds / 60} 分 ${seconds % 60} 秒", style = MaterialTheme.typography.titleLarge)
                AdaptiveStatGrid(
                    listOf(
                        AdaptiveStat("总尝试", attempts.toString()),
                        AdaptiveStat("成功", successes.toString()),
                        AdaptiveStat("失败", failures.toString()),
                        AdaptiveStat("成功率", if (attempts == 0) "尚未记录结果" else "%.0f%%".format(successes * 100.0 / attempts)),
                        AdaptiveStat("最佳连续", streak.toString()),
                    ),
                    modifier = Modifier.testTag("practice_result_stats").semantics {
                        contentDescription = "总尝试 $attempts，成功 $successes，失败 $failures，最佳连续 $streak"
                    },
                )
                Text("练习和弦：$symbols", style = MaterialTheme.typography.bodyLarge)
                hardestTransition?.let { Text("本次最困难切换：$it", style = MaterialTheme.typography.bodyLarge) }
                previousRate?.let {
                    val currentRate = if (attempts == 0) null else successes.toDouble() / attempts
                    val delta = currentRate?.minus(it)
                    Text(
                        when {
                            delta == null -> "本次尚未记录结果，无法比较成功率"
                            delta >= 0 -> "比最近一次成功率提高 ${"%.0f".format(delta * 100)} 个百分点"
                            else -> "比最近一次成功率降低 ${"%.0f".format(-delta * 100)} 个百分点"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("难度建议：$suggestionReason", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("再练一次") }
        if (suggestedBpm > 0) {
            Button(onClick = onSuggestedAgain, modifier = Modifier.fillMaxWidth()) {
                Text("使用建议（$suggestedBpm BPM）再练一次")
            }
        }
        TextButton(onClick = onAdjust) { Text("调整设置") }
        TextButton(onClick = onDone) { Text("完成") }
    }
}

private fun formatTimer(milliseconds: Long): String {
    val totalSeconds = ((milliseconds + 999L) / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Int?.orZero(): Int = this ?: 0
