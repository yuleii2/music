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
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onNavigateToChord: (String) -> Unit,
    onNavigateToRecognition: () -> Unit,
    onNavigateToTranspose: () -> Unit,
    onNavigateToProgressions: () -> Unit,
    onStartPractice: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(HomeViewModel::class) { handle ->
            HomeViewModel(services.chordCatalogGateway, services.userLibraryGateway, handle)
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
        onNavigateToRecognition = onNavigateToRecognition,
        onNavigateToTranspose = onNavigateToTranspose,
        onNavigateToProgressions = onNavigateToProgressions,
        onStartPractice = onStartPractice,
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
    onNavigateToRecognition: () -> Unit,
    onNavigateToTranspose: () -> Unit,
    onNavigateToProgressions: () -> Unit,
    onStartPractice: () -> Unit,
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
                onNavigateToRecognition,
                onNavigateToTranspose,
                onNavigateToProgressions,
                onStartPractice,
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
    onNavigateToRecognition: () -> Unit,
    onNavigateToTranspose: () -> Unit,
    onNavigateToProgressions: () -> Unit,
    onStartPractice: () -> Unit,
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
                Text("Studio Flow", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "把下一次和弦切换练得更顺。",
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
        item("continue") {
            Card(
                onClick = onStartPractice,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp).size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.recent.isEmpty()) "开始第一次练习" else "继续练习",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text("用最近的和弦快速热身", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Rounded.TrendingFlat, contentDescription = null)
                }
            }
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
        item("recommend-title") { SectionTitle("入门推荐", "离线可用") }
        item("recommend-list") {
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
        item("tools-title") { SectionTitle("快捷工具", "两次点击内开始") }
        item("tools") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeToolRow(Icons.Rounded.GraphicEq, "反向识别", "从指板或音符找和弦", onNavigateToRecognition)
                HomeToolRow(Icons.Rounded.SwapHoriz, "移调与变调夹", "快速换调并保留 slash bass", onNavigateToTranspose)
                HomeToolRow(Icons.Rounded.Piano, "和弦进行", "编排、推荐和播放", onNavigateToProgressions)
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
