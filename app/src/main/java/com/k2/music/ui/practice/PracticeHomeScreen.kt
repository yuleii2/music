package com.k2.music.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeHomeData
import com.k2.music.ui.gateway.PracticeModeUi

@Composable
fun PracticeHomeRoute(
    services: CoreServices,
    onSetup: (PracticeConfigUi) -> Unit,
    onAiPlan: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(PracticeHomeViewModel::class) { PracticeHomeViewModel(services.practiceGateway) }
    }
    val viewModel: PracticeHomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    PracticeHomeScreen(state, onSetup, onAiPlan, viewModel::refresh)
}

@Composable
fun PracticeHomeScreen(
    state: PracticeHomeUiState,
    onSetup: (PracticeConfigUi) -> Unit,
    onAiPlan: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("practice_home_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("练习", style = MaterialTheme.typography.headlineLarge)
                Text("把设置、沉浸练习和结果分开，专注完成当前切换。", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (state.loading) {
            item("loading") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        }
        state.error?.let {
            item("error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineMessage(it, isError = true)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
        state.data?.let { data ->
            item("quick") { QuickStartCard(data, onSetup) }
            item("modes_title") { Text("练习模式", style = MaterialTheme.typography.titleLarge) }
            item("modes") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PracticeModeCard(
                        PracticeModeUi.TWO_CHORD,
                        "在两个和弦间建立稳定肌肉记忆。",
                        Icons.Rounded.SwapHoriz,
                    ) { onSetup(data.quickConfig.copy(mode = PracticeModeUi.TWO_CHORD)) }
                    PracticeModeCard(
                        PracticeModeUi.MULTI_CHORD,
                        "循环整段和弦，练习连续切换。",
                        Icons.Rounded.Repeat,
                    ) { onSetup(data.quickConfig.copy(mode = PracticeModeUi.MULTI_CHORD)) }
                    PracticeModeCard(
                        PracticeModeUi.RANDOM,
                        "使用确定性随机顺序提高反应速度。",
                        Icons.Rounded.Casino,
                    ) { onSetup(data.quickConfig.copy(mode = PracticeModeUi.RANDOM)) }
                }
            }
            item("stats") { PracticeStats(data) }
            item("ai_plan") {
                TextButton(
                    onClick = {
                        val summary = data.summary
                        onAiPlan(
                            "本地练习摘要：今日 ${summary.todaySeconds} 秒；近 7 天 ${summary.sevenDaySessions} 次、${summary.sevenDaySeconds} 秒；" +
                                "常练和弦 ${summary.mostPracticedChord.ifBlank { "暂无" }}；最佳连续 ${summary.bestStreak}。",
                        )
                    },
                ) { Text("可选：让 AI 建议练习计划") }
            }
        }
    }
}

@Composable
private fun QuickStartCard(data: PracticeHomeData, onSetup: (PracticeConfigUi) -> Unit) {
    val config = data.quickConfig
    Card(
        onClick = { onSetup(config) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("practice_quick_start"),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("快速开始", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${config.mode.label} · ${config.symbols} · ${config.durationSeconds} 秒 · ${config.bpm} BPM",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text("设置", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PracticeModeCard(mode: PracticeModeUi, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PracticeStats(data: PracticeHomeData) {
    val summary = data.summary
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("最近统计", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("今日", "${summary.todaySeconds / 60} 分钟", Modifier.weight(1f))
            StatCard("近 7 天", "${summary.sevenDaySessions} 次", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("常练和弦", summary.mostPracticedChord.ifBlank { "尚无" }, Modifier.weight(1f))
            StatCard("最佳连续", "${summary.bestStreak} 次", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
