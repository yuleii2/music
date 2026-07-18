package com.k2.music.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.ChordCard
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.LoadingSkeleton
import com.k2.music.ui.components.StudioGroup
import com.k2.music.ui.components.StudioListItem
import com.k2.music.ui.components.StudioPageHeader
import com.k2.music.ui.components.StudioSearchField
import com.k2.music.ui.components.StudioSectionHeader
import com.k2.music.ui.components.StudioTopAppBar
import com.k2.music.ui.theme.LocalMusicMotion
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.learning.DailyPracticeTask
import com.k2.music.ui.learning.DailyTaskType
import com.k2.music.song.SongPracticeMode
import com.k2.music.ui.song.SongHomeTask
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onNavigateToChord: (String) -> Unit,
    onNavigateToTools: () -> Unit,
    onStartPractice: (PracticeConfigUi) -> Unit,
    onAdjustPractice: (PracticeConfigUi) -> Unit,
    onSongTask: (SongHomeTask) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(HomeViewModel::class) { handle ->
            HomeViewModel(
                services.chordCatalogGateway,
                services.userLibraryGateway,
                services.practiceGateway,
                { services.learningProfileStore.profile.value },
                handle,
                services.songGateway,
            )
        }
    }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomeEffect.NavigateToChord -> onNavigateToChord(effect.symbol)
                is HomeEffect.RecentRemoved -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已从最近查看移除 ${effect.symbol}",
                        actionLabel = "撤销",
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.restoreRecent(effect.symbol)
                    }
                }
                is HomeEffect.NavigateToSongTask -> onSongTask(effect.task)
            }
        }
    }
    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onQueryChange = viewModel::updateQuery,
        onSubmitSearch = viewModel::submitSearch,
        onOpenChord = viewModel::openChord,
        onToggleFavorite = viewModel::toggleFavorite,
        onRemoveRecent = viewModel::removeRecent,
        onNavigateToTools = onNavigateToTools,
        onStartPractice = onStartPractice,
        onAdjustPractice = onAdjustPractice,
        onSongTask = viewModel::startSongTask,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onOpenChord: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onNavigateToTools: () -> Unit,
    onStartPractice: (PracticeConfigUi) -> Unit,
    onAdjustPractice: (PracticeConfigUi) -> Unit,
    onSongTask: (SongHomeTask) -> Unit,
) {
    val motion = LocalMusicMotion.current
    BackHandler(enabled = state.searchActive, onBack = onCloseSearch)
    Crossfade(
        targetState = state.searchActive,
        animationSpec = tween(motion.standard),
        label = "home-search",
    ) { searchActive ->
        if (searchActive) {
            FullSearchScreen(
                state,
                snackbarHostState,
                onCloseSearch,
                onQueryChange,
                onSubmitSearch,
                onOpenChord,
                onToggleFavorite,
            )
        } else if (state.loading) {
            LoadingSkeleton()
        } else {
            HomeContent(
                state,
                onOpenSearch,
                onOpenChord,
                onToggleFavorite,
                onRemoveRecent,
                onNavigateToTools,
                onStartPractice,
                onAdjustPractice,
                onSongTask,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenSearch: () -> Unit,
    onOpenChord: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onNavigateToTools: () -> Unit,
    onStartPractice: (PracticeConfigUi) -> Unit,
    onAdjustPractice: (PracticeConfigUi) -> Unit,
    onSongTask: (SongHomeTask) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_content"),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("header") {
            StudioPageHeader("概览", "和弦、曲谱与练习都留在本机。")
        }
        state.fallbackMessage?.let { message ->
            item("fallback") { InlineMessage("正在使用安全离线数据。$message") }
        }
        item("search") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(onClick = onOpenSearch)
                    .testTag("home_search_launcher"),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "输入 Cmaj7、Am、G/B……",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val plan = state.dailyPlan
        val completedMinutes = state.practiceSummary.todaySeconds / 60
        item("today-status") {
            StudioGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("今日练习", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "$completedMinutes / ${plan?.targetMinutes ?: 5} 分钟",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val remaining = ((plan?.targetMinutes ?: 5) - completedMinutes).coerceAtLeast(0)
                    Text(
                        if (remaining == 0L) "今日计划已完成，可按需要继续复习。" else "还有 $remaining 分钟计划内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.practiceSummary.learningStreakDays > 0) {
                        Text(
                            "连续记录 ${state.practiceSummary.learningStreakDays} 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.songTasks.firstOrNull()?.let { task ->
                        StudioButton(onClick = { onSongTask(task) }, enabled = !state.songTaskStarting) {
                            Text(if (state.songTaskStarting) "正在恢复…" else "继续：${task.title}")
                        }
                    } ?: plan?.tasks?.firstOrNull { it.config != null && it.type != DailyTaskType.CONTINUE_LAST }?.let { task ->
                        StudioButton(onClick = { onStartPractice(requireNotNull(task.config)) }) { Text("开始：${task.title}") }
                    }
                }
            }
        }
        state.songTaskError?.let { message ->
            item("song-task-error") { InlineMessage(message, isError = true) }
        }
        if (state.songTasks.isNotEmpty()) {
            item("song-plan-title") { SectionTitle("曲谱会话", "来自本地练习记录") }
            items(state.songTasks, key = { "song-${it.type}-${it.songId}-${it.sectionId}" }) { task ->
                SongTaskCard(task, !state.songTaskStarting) { onSongTask(task) }
            }
        }
        plan?.tasks?.firstOrNull { it.type == DailyTaskType.CONTINUE_LAST }?.let { task ->
            item("continue") { PracticeTaskCard(task, onStartPractice, onAdjustPractice, directLabel = "直接继续") }
        } ?: item("continue-empty") {
            plan?.tasks?.firstOrNull { it.config != null }?.let { task ->
                PracticeTaskCard(task.copy(title = "开始第一次练习"), onStartPractice, onAdjustPractice)
            }
        }
        val reviewTasks = plan?.tasks.orEmpty().filter {
            it.type == DailyTaskType.REVIEW_STALE_CHORD || it.type == DailyTaskType.PRACTICE_PROGRESSION
        }
        if (reviewTasks.isNotEmpty()) {
            item("review-title") { SectionTitle("待复习", "来自学习资料与历史") }
            items(reviewTasks, key = { "review-${it.type}" }) { task ->
                PracticeTaskCard(task, onStartPractice, onAdjustPractice)
            }
        }
        plan?.weakestTransition?.let { task ->
            item("weak-title") { SectionTitle("重点切换", "至少 5 次有效样本") }
            item("weak") { PracticeTaskCard(task, onStartPractice, onAdjustPractice) }
        }
        if (state.recent.isNotEmpty()) {
            item("recent-title") { SectionTitle("最近查看", "长按可移除") }
            item("recent-list") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recent, key = { it.symbol }) { chord ->
                        ChordCard(
                            chord = chord,
                            onClick = { onOpenChord(chord.symbol) },
                            onFavoriteClick = { onToggleFavorite(chord.symbol) },
                            onLongClick = { onRemoveRecent(chord.symbol) },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }
            }
        }
        if (state.recommendations.isNotEmpty()) item("recommend-title") { SectionTitle("和弦参考", "由本地资料生成") }
        if (state.recommendations.isNotEmpty()) item("recommend-list") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.recommendations, key = { it.symbol }) { chord ->
                    ChordCard(
                        chord = chord,
                        onClick = { onOpenChord(chord.symbol) },
                        onFavoriteClick = { onToggleFavorite(chord.symbol) },
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
        }
        item("tools") {
            HomeToolRow(
                icon = Icons.Rounded.GraphicEq,
                title = "全部工具",
                subtitle = "识别、移调、和弦进行与节拍器",
                onClick = onNavigateToTools,
            )
        }
    }
}

@Composable
private fun SongTaskCard(task: SongHomeTask, enabled: Boolean, onStart: () -> Unit) {
    StudioGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(task.reason, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${task.bpm} BPM · 移调 ${"%+d".format(task.transposeSemitones)} · 变调夹 ${task.capoFret} 品" +
                    if (task.mode == SongPracticeMode.PERFORMANCE) {
                        " · ${if (task.loopEnabled) "循环" else "不循环"} · ${if (task.showFretboard) "显示指板" else "隐藏指板"}"
                    } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudioButton(onClick = onStart, enabled = enabled) {
                Text(
                    when (task.mode) {
                        SongPracticeMode.PERFORMANCE -> "继续连续演奏"
                        SongPracticeMode.GUIDED_TRANSITION -> "开始专项切换"
                        null -> "打开曲谱学习"
                    },
                )
            }
        }
    }
}

@Composable
private fun PracticeTaskCard(
    task: DailyPracticeTask,
    onStart: (PracticeConfigUi) -> Unit,
    onAdjust: (PracticeConfigUi) -> Unit,
    directLabel: String = "开始练习",
) {
    StudioGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(task.reason, style = MaterialTheme.typography.bodyMedium)
            task.config?.let { config ->
                Text("${config.symbols} · ${config.bpm} BPM · ${config.durationSeconds} 秒")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioButton(onClick = { onStart(config) }) { Text(directLabel) }
                    TextButton(onClick = { onAdjust(config) }) { Text("调整设置") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FullSearchScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onOpenChord: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Scaffold(
        topBar = {
            StudioTopAppBar(
                title = { Text("搜索和弦") },
                navigationIcon = {
                    IconButton(onClick = onCloseSearch) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "关闭搜索")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("field") {
                StudioSearchField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("home_search_field"),
                    placeholder = "和弦名称或类型，例如 Cmaj7、Am、G/B",
                    isError = state.searchError != null,
                    supportingText = state.searchError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                )
            }
            if (state.searching) {
                item("progress") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (state.searchQuery.isBlank()) {
                item("examples") {
                    InlineMessage("可尝试 C、Am、G7、Fmaj7、C7b9、Cm7b5 或 G/B。")
                }
            }
            items(state.searchResults, key = { it.symbol }, contentType = { "chord-result" }) { chord ->
                ChordCard(
                    chord = chord,
                    onClick = { onOpenChord(chord.symbol) },
                    onFavoriteClick = { onToggleFavorite(chord.symbol) },
                )
            }
            if (!state.searching && state.searchQuery.isNotBlank() && state.searchResults.isEmpty() && state.searchError == null) {
                item("empty") { InlineMessage("没有匹配结果。若输入的是完整和弦名，可按键盘搜索键直接校验理论和弦。") }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    StudioSectionHeader(title, trailing)
}

@Composable
private fun HomeToolRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    StudioGroup {
        StudioListItem(
            title = title,
            subtitle = subtitle,
            icon = icon,
            onClick = onClick,
        )
    }
}
