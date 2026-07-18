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
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.LibraryMusic
import com.k2.music.ui.components.StudioButton
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
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.components.StudioGroup
import com.k2.music.ui.components.StudioListItem
import com.k2.music.ui.components.StudioPageHeader
import com.k2.music.ui.components.StudioSectionHeader

@Composable
fun PracticeHomeRoute(
    services: CoreServices,
    onSetup: (PracticeConfigUi) -> Unit,
    onStartDirect: (PracticeConfigUi) -> Unit,
    onAiPlan: (String) -> Unit,
    onSongLibrary: () -> Unit,
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
    PracticeHomeScreen(state, onSetup, onStartDirect, onAiPlan, onSongLibrary, viewModel::refresh)
}

@Composable
fun PracticeHomeScreen(
    state: PracticeHomeUiState,
    onSetup: (PracticeConfigUi) -> Unit,
    onStartDirect: (PracticeConfigUi) -> Unit,
    onAiPlan: (String) -> Unit,
    onSongLibrary: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("practice_home_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            StudioPageHeader("练习", "设置一次会话，然后只关注节拍与切换。")
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
            item("song_library") {
                StudioGroup {
                    StudioListItem(
                        title = "本地曲谱",
                        subtitle = "按段落练习切换，或进入连续演奏。",
                        icon = Icons.Rounded.LibraryMusic,
                        onClick = onSongLibrary,
                        testTag = "song_library_entry",
                    )
                }
            }
            item("quick") { QuickStartCard(data, onStartDirect, onSetup) }
            item("modes_title") { StudioSectionHeader("会话类型") }
            item("modes") {
                StudioGroup {
                    PracticeModeCard(
                        PracticeModeUi.TWO_CHORD,
                        "在两个和弦间建立稳定肌肉记忆。",
                        Icons.Rounded.SwapHoriz,
                        showDivider = true,
                    ) { onSetup(data.quickConfig.copy(mode = PracticeModeUi.TWO_CHORD)) }
                    PracticeModeCard(
                        PracticeModeUi.MULTI_CHORD,
                        "循环整段和弦，练习连续切换。",
                        Icons.Rounded.Repeat,
                        showDivider = true,
                    ) { onSetup(data.quickConfig.copy(mode = PracticeModeUi.MULTI_CHORD)) }
                    PracticeModeCard(
                        PracticeModeUi.RANDOM,
                        "打乱顺序，提高识别与切换速度。",
                        Icons.Rounded.Shuffle,
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
private fun QuickStartCard(
    data: PracticeHomeData,
    onStart: (PracticeConfigUi) -> Unit,
    onSetup: (PracticeConfigUi) -> Unit,
) {
    val config = data.quickConfig
    StudioGroup {
        StudioListItem(
            title = "继续上次设置",
            subtitle = "${config.mode.label} · ${config.symbols} · ${config.durationSeconds} 秒 · ${config.bpm} BPM",
            icon = Icons.Rounded.PlayCircle,
            onClick = { onStart(config) },
            testTag = "practice_quick_start",
            trailing = { TextButton(onClick = { onSetup(config) }) { Text("调整") } },
        )
    }
}

@Composable
private fun PracticeModeCard(
    mode: PracticeModeUi,
    detail: String,
    icon: ImageVector,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    StudioListItem(
        title = mode.label,
        subtitle = detail,
        icon = icon,
        onClick = onClick,
        showDivider = showDivider,
    )
}

@Composable
private fun PracticeStats(data: PracticeHomeData) {
    val summary = data.summary
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudioSectionHeader("最近统计", "仅保存在本机")
        AdaptiveStatGrid(
            listOf(
                AdaptiveStat("今日", "${summary.todaySeconds / 60} 分钟"),
                AdaptiveStat("近 7 天练习", "${summary.sevenDaySessions} 次"),
                AdaptiveStat("近 7 天尝试", "${summary.sevenDayAttempts} 次"),
                AdaptiveStat("近 7 天成功率", summary.sevenDaySuccessRate?.let { "%.0f%%".format(it * 100) } ?: "数据不足"),
                AdaptiveStat("最需复习", summary.weakestTransition?.key?.label ?: "数据不足"),
                AdaptiveStat("最高稳定速度", summary.highestStableBpm?.let { "$it BPM" } ?: "数据不足"),
            ),
        )
    }
}
