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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.ChordCard
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.LoadingSkeleton
import com.k2.music.ui.theme.LocalMusicMotion
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.learning.DailyPracticeTask
import com.k2.music.ui.learning.DailyTaskType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onNavigateToChord: (String) -> Unit,
    onNavigateToTools: () -> Unit,
    onStartPractice: (PracticeConfigUi) -> Unit,
    onAdjustPractice: (PracticeConfigUi) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(HomeViewModel::class) { handle ->
            HomeViewModel(
                services.chordCatalogGateway,
                services.userLibraryGateway,
                services.practiceGateway,
                { services.learningProfileStore.profile.value },
                handle,
            )
        }
    }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
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
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_content"),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("header") {
            Column {
                Text("吉他和弦工作室", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "查询和弦、练习切换、编排进行并记录进步。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.fallbackMessage?.let { message ->
            item("fallback") { InlineMessage("正在使用安全离线数据。$message") }
        }
        item("search") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(onClick = onOpenSearch)
                    .testTag("home_search_launcher"),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("今日练习", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "已练 $completedMinutes / ${plan?.targetMinutes ?: 5} 分钟",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    val remaining = ((plan?.targetMinutes ?: 5) - completedMinutes).coerceAtLeast(0)
                    Text(if (remaining == 0L) "今天的目标已完成，可以轻松复习。" else "再完成 $remaining 分钟即可达到今日目标。")
                    if (state.practiceSummary.learningStreakDays > 0) {
                        Text("已连续学习 ${state.practiceSummary.learningStreakDays} 天", style = MaterialTheme.typography.bodyMedium)
                    }
                    plan?.tasks?.firstOrNull { it.config != null && it.type != DailyTaskType.CONTINUE_LAST }?.let { task ->
                        Button(onClick = { onStartPractice(requireNotNull(task.config)) }) { Text("开始：${task.title}") }
                    }
                }
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
            item("review-title") { SectionTitle("今日复习", "来自学习资料与历史") }
            items(reviewTasks, key = { "review-${it.type}" }) { task ->
                PracticeTaskCard(task, onStartPractice, onAdjustPractice)
            }
        }
        plan?.weakestTransition?.let { task ->
            item("weak-title") { SectionTitle("最需要复习的切换", "至少 5 次有效样本") }
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
        if (state.recommendations.isNotEmpty()) item("recommend-title") { SectionTitle("推荐新内容", "推荐理由可解释") }
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
        item("tools") { TextButton(onClick = onNavigateToTools) { Text("查看全部工具") } }
    }
}

@Composable
private fun PracticeTaskCard(
    task: DailyPracticeTask,
    onStart: (PracticeConfigUi) -> Unit,
    onAdjust: (PracticeConfigUi) -> Unit,
    directLabel: String = "开始练习",
) {
    Card(modifier = Modifier.fillMaxWidth(), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            Text(task.reason, style = MaterialTheme.typography.bodyMedium)
            task.config?.let { config ->
                Text("${config.symbols} · ${config.bpm} BPM · ${config.durationSeconds} 秒")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStart(config) }) { Text(directLabel) }
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
            TopAppBar(
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
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("home_search_field"),
                    label = { Text("和弦名称或类型") },
                    placeholder = { Text("Cmaj7、Am、G/B") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeToolRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.TrendingFlat, contentDescription = "打开$title")
        }
    }
}
