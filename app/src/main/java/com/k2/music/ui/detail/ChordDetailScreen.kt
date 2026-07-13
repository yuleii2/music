package com.k2.music.ui.detail

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.ChordTag
import com.k2.music.ui.components.ErrorState
import com.k2.music.ui.components.FretboardCanvas
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.gateway.PlaybackUiState
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.VoicingUiModel
import com.k2.music.ui.theme.LocalMusicMotion
import com.k2.music.ui.navigation.sharedChordBounds
import com.k2.music.ui.preferences.LocalExperienceCapabilities
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChordDetailRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onExportCurrent: (String, Int) -> Unit,
    onExportAll: (String) -> Unit,
    onExplainWithAi: (String) -> Unit,
    onStartPractice: (String) -> Unit,
    onAddProgression: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(ChordDetailViewModel::class) { handle ->
            ChordDetailViewModel(
                services.chordCatalogGateway,
                services.userLibraryGateway,
                services.playbackController,
                handle,
            )
        }
    }
    val viewModel: ChordDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val capabilities = LocalExperienceCapabilities.current
    LaunchedEffect(capabilities, state.chord?.symbol) {
        viewModel.applyExperienceMode(
            capabilities.showAdvancedTheoryByDefault,
            capabilities.showAllVoicingsByDefault,
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ChordDetailEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    ChordDetailScreen(
        state = state,
        onBack = onNavigateBack,
        onRetry = viewModel::retry,
        onSelectVoicing = viewModel::selectVoicing,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleFamiliar = viewModel::toggleFamiliar,
        onPlay = viewModel::play,
        onToggleTheory = viewModel::toggleTheory,
        onDeleteCustomVoicing = viewModel::deleteSelectedCustomVoicing,
        onExportCurrent = { state.chord?.let { onExportCurrent(it.symbol, state.selectedVoicingIndex) } },
        onExportAll = { state.chord?.let { onExportAll(it.symbol) } },
        onExplainWithAi = { state.chord?.let { onExplainWithAi(it.symbol) } },
        onStartPractice = { state.chord?.let { onStartPractice(it.symbol) } },
        onAddProgression = { state.chord?.let { onAddProgression(it.symbol) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordDetailScreen(
    state: ChordDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectVoicing: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFamiliar: () -> Unit,
    onPlay: () -> Unit,
    onToggleTheory: () -> Unit,
    onDeleteCustomVoicing: () -> Unit,
    onExportCurrent: () -> Unit,
    onExportAll: () -> Unit,
    onExplainWithAi: () -> Unit,
    onStartPractice: () -> Unit,
    onAddProgression: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val chord = state.chord
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("chord_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text("和弦详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("使用该和弦开始练习") },
                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                            enabled = chord != null,
                            onClick = { menuExpanded = false; onStartPractice() },
                        )
                        DropdownMenuItem(
                            text = { Text("加入和弦进行") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            enabled = chord != null,
                            onClick = { menuExpanded = false; onAddProgression() },
                        )
                        DropdownMenuItem(
                            text = { Text("查看适合的切换练习") },
                            leadingIcon = { Icon(Icons.Rounded.School, contentDescription = null) },
                            enabled = chord != null,
                            onClick = { menuExpanded = false; onStartPractice() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.familiar) "取消熟悉按法" else "标记为熟悉按法") },
                            leadingIcon = { Icon(Icons.Rounded.School, contentDescription = null) },
                            enabled = state.selectedVoicing != null,
                            onClick = { menuExpanded = false; onToggleFamiliar() },
                        )
                        DropdownMenuItem(
                            text = { Text("导出当前按法") },
                            leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                            enabled = state.selectedVoicing != null,
                            onClick = { menuExpanded = false; onExportCurrent() },
                        )
                        DropdownMenuItem(
                            text = { Text("导出全部按法") },
                            leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                            enabled = chord?.voicings?.isNotEmpty() == true,
                            onClick = { menuExpanded = false; onExportAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("AI 解释这个和弦") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                            enabled = chord != null,
                            onClick = { menuExpanded = false; onExplainWithAi() },
                        )
                        if (state.selectedVoicing?.isCustom == true) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("删除自定义指法") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = { menuExpanded = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (chord != null) {
                DetailActions(
                    chord = chord,
                    favorite = state.favorite,
                    playback = state.playback,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> ErrorState(state.error, onRetry, Modifier.padding(padding))
            chord != null -> DetailContent(
                state = state,
                chord = chord,
                modifier = Modifier.padding(padding),
                onSelectVoicing = onSelectVoicing,
                onToggleTheory = onToggleTheory,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除自定义指法？") },
            text = { Text("只会删除你保存的这条指法；内置指法和和弦数据不会改变。") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDeleteCustomVoicing() }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailContent(
    state: ChordDetailUiState,
    chord: ChordUiModel,
    modifier: Modifier,
    onSelectVoicing: (Int) -> Unit,
    onToggleTheory: () -> Unit,
) {
    val capabilities = LocalExperienceCapabilities.current
    val visibleIndices = if (capabilities.showAllVoicingsByDefault) {
        chord.voicings.indices.toList()
    } else {
        listOf(chord.voicings.indexOfFirst { it.recommended }.takeIf { it >= 0 } ?: 0)
            .filter { it in chord.voicings.indices }
    }
    val visibleChord = chord.copy(voicings = visibleIndices.map { chord.voicings[it] })
    val visibleSelected = visibleIndices.indexOf(state.selectedVoicingIndex).coerceAtLeast(0)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("chord_detail_content"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("hero") {
            Surface(
                modifier = Modifier.fillMaxWidth().sharedChordBounds(chord.symbol, "container"),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        chord.symbol,
                        modifier = Modifier.sharedChordBounds(chord.symbol, "title"),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(chord.chineseName, style = MaterialTheme.typography.titleLarge)
                    if (chord.bassNote.isNotBlank()) {
                        Text("低音：${chord.bassNote}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        state.infoMessage?.let { message -> item("message") { InlineMessage(message) } }
        item("summary") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("root") { SummaryPill("根音", chord.root) }
                item("notes") { SummaryPill("组成音", chord.notes.joinToString(" · ")) }
                if (capabilities.showTechnicalLabels) {
                    item("intervals") { SummaryPill("音程", chord.intervals.joinToString(" · ")) }
                }
            }
        }
        if (chord.voicings.isEmpty()) {
            item("no-voicing") {
                InlineMessage("该和弦理论数据可用，当前暂无收录指法。主操作会试听组成音。")
            }
        } else {
            item("fretboard") {
                key(chord.symbol) {
                    VoicingPager(visibleChord, visibleSelected) { visible ->
                        visibleIndices.getOrNull(visible)?.let(onSelectVoicing)
                    }
                }
            }
            item("selector") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(visibleChord.voicings, key = { _, voicing -> voicing.id }) { index, voicing ->
                        FilterChip(
                            selected = index == visibleSelected,
                            onClick = { visibleIndices.getOrNull(index)?.let(onSelectVoicing) },
                            label = { Text("${index + 1}. ${voicing.name}") },
                        )
                    }
                }
            }
            state.selectedVoicing?.let { voicing ->
                item("voicing-info") { VoicingInfo(voicing, state.familiar) }
            }
        }
        item("theory-toggle") {
            Surface(
                onClick = onToggleTheory,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("和弦说明与学习提示", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        if (state.theoryExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (state.theoryExpanded) "收起" else "展开",
                    )
                }
            }
        }
        if (state.theoryExpanded) {
            item("theory") {
                Text(chord.description.ifBlank { "当前没有更多说明。" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item("bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun VoicingPager(chord: ChordUiModel, selectedIndex: Int, onSelectVoicing: (Int) -> Unit) {
    val motion = LocalMusicMotion.current
    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceIn(0, chord.voicings.lastIndex)) {
        chord.voicings.size
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(onSelectVoicing)
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != pagerState.currentPage && selectedIndex in chord.voicings.indices) {
            if (motion.emphasized == 0) pagerState.scrollToPage(selectedIndex)
            else pagerState.animateScrollToPage(selectedIndex)
        }
    }
    HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
        Surface(
            modifier = Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            FretboardCanvas(
                chord = chord,
                voicing = chord.voicings[page],
                modifier = Modifier
                    .sharedChordBounds(chord.symbol, "fretboard")
                    .fillMaxSize()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun VoicingInfo(voicing: VoicingUiModel, familiar: Boolean) {
    val capabilities = LocalExperienceCapabilities.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveControlGroup {
            ChordTag(voicing.difficulty)
            if (voicing.recommended) ChordTag("推荐")
            if (voicing.simplified) ChordTag("简化")
            if (voicing.barre) ChordTag("横按")
            if (voicing.isCustom) ChordTag("自定义")
            if (familiar) ChordTag("已熟悉")
        }
        Text(
            "六弦到一弦：${voicing.frets.joinToString(" ") { if (it < 0) "X" else if (it == 0) "O" else it.toString() }}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "实际发声：${voicing.stringNotes.filterNotNull().joinToString(" · ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!capabilities.showTechnicalLabels) {
            val fingers = voicing.fingers.mapIndexedNotNull { index, finger ->
                finger.takeIf { it > 0 }?.let { "${6 - index} 弦用 $it 指" }
            }
            if (fingers.isNotEmpty()) Text("建议手指：${fingers.joinToString("，")}")
            Text(
                when {
                    voicing.barre -> "常见错误：食指横按没有压实；先逐弦检查声音。"
                    voicing.isOpen -> "为什么推荐：包含空弦，手型适合入门和常见伴奏。"
                    else -> "为什么推荐：这是当前资料中的常用按法。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (voicing.description.isNotBlank()) Text(voicing.description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SummaryPill(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DetailActions(
    chord: ChordUiModel,
    favorite: Boolean,
    playback: PlaybackUiState,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val playing = playback is PlaybackUiState.Playing && playback.symbol == chord.symbol
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else if (chord.hasVoicings) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (playing) "停止" else if (chord.hasVoicings) "试听按法" else "试听组成音")
            }
            Surface(
                onClick = onToggleFavorite,
                modifier = Modifier.size(56.dp),
                color = if (favorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "取消收藏，当前已收藏" else "收藏当前和弦",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
