package com.k2.music.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.k2.music.ui.components.StudioOutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.ChordCard
import com.k2.music.ui.components.ChordTag
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.components.EmptyState
import com.k2.music.ui.components.ErrorState
import com.k2.music.ui.components.FretboardCanvas
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.StudioPageHeader
import com.k2.music.ui.components.StudioSearchField
import com.k2.music.ui.components.StudioSegmentedControl
import com.k2.music.ui.gateway.LibraryFilter
import com.k2.music.ui.model.AccidentalPreference
import com.k2.music.ui.model.ChordFamily
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.displaySymbol
import com.k2.music.ui.model.rootChoiceLabel
import com.k2.music.ui.theme.LocalMusicMotion
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LibraryRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onNavigateToChord: (String) -> Unit,
    onNavigateToRecognition: () -> Unit,
    onExportSelection: (Set<String>) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(LibraryViewModel::class) { handle ->
            LibraryViewModel(services.chordCatalogGateway, services.userLibraryGateway, handle)
        }
    }
    val viewModel: LibraryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is LibraryEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is LibraryEffect.FavoritesChanged -> {
                    val result = snackbarHostState.showSnackbar(effect.text, actionLabel = "撤销")
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undoFavoriteChange(effect.symbols, effect.favorite)
                    }
                }
            }
        }
    }
    LibraryScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onSegmentSelected = viewModel::setSegment,
        onFilterApplied = viewModel::setFilter,
        onClearFilters = viewModel::clearFilters,
        onOpenChord = onNavigateToChord,
        onToggleFavorite = viewModel::toggleFavorite,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onFavoriteSelection = viewModel::toggleFavoriteSelection,
        onExportSelection = { onExportSelection(state.selectedSymbols) },
        onBrowse = { viewModel.setSegment(LibrarySegment.ALL) },
        onCreateCustom = onNavigateToRecognition,
        onRetry = viewModel::retry,
        onAccidentalPreferenceChanged = viewModel::setAccidentalPreference,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onSegmentSelected: (LibrarySegment) -> Unit,
    onFilterApplied: (LibraryFilter) -> Unit,
    onClearFilters: () -> Unit,
    onOpenChord: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onFavoriteSelection: () -> Unit,
    onExportSelection: () -> Unit,
    onBrowse: () -> Unit,
    onCreateCustom: () -> Unit,
    onRetry: () -> Unit,
    onAccidentalPreferenceChanged: (AccidentalPreference) -> Unit = {},
) {
    var showFilters by remember { mutableStateOf(false) }
    BackHandler(enabled = state.selectionMode, onBack = onClearSelection)
    Column(Modifier.fillMaxSize().testTag("library_screen")) {
        if (state.selectionMode) {
            SelectionBar(
                count = state.selectedSymbols.size,
                onClose = onClearSelection,
                onFavorite = onFavoriteSelection,
                onExport = onExportSelection,
            )
        } else {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                StudioPageHeader("和弦", "本地和弦与指法资料库。")
                Spacer(Modifier.height(12.dp))
                StudioSearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().testTag("library_search_field"),
                    placeholder = "名称、类型、音名或别名",
                )
                Spacer(Modifier.height(10.dp))
                StudioSegmentedControl(
                    options = LibrarySegment.entries.map { it to it.label },
                    selected = state.segment,
                    onSelected = onSegmentSelected,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.chords.size} 个结果",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showFilters = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.filter.isActive) {
                                "筛选 · ${activeFilterLabels(state.filter, state.qualities).size}"
                            } else {
                                "筛选"
                            },
                        )
                    }
                }
                if (state.filter.isActive) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item("clear") {
                            AssistChip(onClick = onClearFilters, label = { Text("清除全部") })
                        }
                        activeFilterLabels(state.filter, state.qualities).forEach { label ->
                            item(label) { AssistChip(onClick = { showFilters = true }, label = { Text(label) }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().testTag("library_loading"),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> ErrorState(state.error, onRetry)
            state.chords.isEmpty() -> LibraryEmptyState(state.segment, state.query, onBrowse, onCreateCustom, onClearFilters)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 760.dp
                var focusedSymbol by rememberSaveable(state.segment.name) { mutableStateOf<String?>(null) }
                val focusedChord = state.chords.firstOrNull { it.symbol == focusedSymbol }
                    ?: state.chords.firstOrNull()
                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        ChordGrid(
                            state = state,
                            onOpenChord = { focusedSymbol = it },
                            onToggleFavorite = onToggleFavorite,
                            onEnterSelection = onEnterSelection,
                            onToggleSelection = onToggleSelection,
                            focusedSymbol = focusedChord?.symbol,
                            modifier = Modifier.weight(0.54f),
                        )
                        VerticalDivider()
                        LibraryDetailPane(
                            chord = focusedChord,
                            accidentalPreference = state.accidentalPreference,
                            onOpenChord = onOpenChord,
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier.weight(0.46f),
                        )
                    }
                } else {
                    ChordGrid(
                        state = state,
                        onOpenChord = onOpenChord,
                        onToggleFavorite = onToggleFavorite,
                        onEnterSelection = onEnterSelection,
                        onToggleSelection = onToggleSelection,
                    )
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(
            current = state.filter,
            roots = state.roots,
            qualities = state.qualities,
            accidentalPreference = state.accidentalPreference,
            onDismiss = { showFilters = false },
            onAccidentalPreferenceChanged = onAccidentalPreferenceChanged,
            onApply = {
                onFilterApplied(it)
                showFilters = false
            },
            onClear = {
                onClearFilters()
                showFilters = false
            },
        )
    }
}

@Composable
private fun ChordGrid(
    state: LibraryUiState,
    onOpenChord: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusedSymbol: String? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val motion = LocalMusicMotion.current
    val minimum = if (fontScale >= 1.6f) 280.dp else 168.dp
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minimum),
        state = gridState,
        modifier = modifier.fillMaxSize().testTag("library_grid"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = state.chords,
            key = { it.symbol },
            contentType = { chord ->
                when {
                    chord.voicings.any { it.isCustom } -> "custom"
                    chord.hasVoicings -> "preview"
                    else -> "theory"
                }
            },
        ) { chord ->
            val selected = chord.symbol in state.selectedSymbols
            ChordCard(
                chord = chord,
                displaySymbol = chord.displaySymbol(state.accidentalPreference),
                selected = selected,
                onClick = {
                    if (state.selectionMode) onToggleSelection(chord.symbol) else onOpenChord(chord.symbol)
                },
                onFavoriteClick = { onToggleFavorite(chord.symbol) },
                onLongClick = {
                    if (state.selectionMode) onToggleSelection(chord.symbol) else onEnterSelection(chord.symbol)
                },
                modifier = Modifier
                    .testTag("library_chord_${chord.symbol}")
                    .then(if (motion.allowSpatialTransitions) Modifier.animateItem() else Modifier)
                    .then(
                        if (!state.selectionMode && chord.symbol == focusedSymbol) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
private fun LibraryDetailPane(
    chord: ChordUiModel?,
    accidentalPreference: AccidentalPreference,
    onOpenChord: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chord == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("选择一个和弦", "从左侧列表选择后在这里预览。")
        }
        return
    }
    var selectedVoicingId by rememberSaveable(chord.symbol) {
        mutableStateOf(chord.voicings.firstOrNull()?.id)
    }
    val voicing = chord.voicings.firstOrNull { it.id == selectedVoicingId }
        ?: chord.voicings.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("library_detail_pane"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chord.displaySymbol(accidentalPreference), style = MaterialTheme.typography.headlineLarge)
                    Text(chord.chineseName, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { onToggleFavorite(chord.symbol) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (chord.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (chord.favorite) {
                            "取消收藏 ${chord.displaySymbol(accidentalPreference)}"
                        } else {
                            "收藏 ${chord.displaySymbol(accidentalPreference)}"
                        },
                    )
                }
            }
        }
        item("notes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(chord.notes.joinToString(" · "), style = MaterialTheme.typography.titleLarge)
                Text("音程：${chord.intervals.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (voicing == null) {
            item("no_voicing") { InlineMessage("这是理论合法和弦，当前没有收录指法；完整详情仍可试听组成音。") }
        } else {
            item("fretboard") {
                FretboardCanvas(
                    chord = chord,
                    voicing = voicing,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
            }
            item("voicing_title") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(voicing.name, style = MaterialTheme.typography.titleLarge)
                    AdaptiveControlGroup {
                        ChordTag(voicing.difficulty)
                        if (voicing.simplified) ChordTag("简化")
                        if (voicing.barre) ChordTag("横按")
                        if (voicing.isOpen) ChordTag("开放")
                        if (voicing.isCustom) ChordTag("自定义")
                    }
                }
            }
            if (chord.voicings.size > 1) {
                item("voicings") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chord.voicings, key = { it.id }) { option ->
                            FilterChip(
                                selected = option.id == voicing.id,
                                onClick = { selectedVoicingId = option.id },
                                label = { Text(option.name) },
                            )
                        }
                    }
                }
            }
        }
        if (chord.description.isNotBlank()) {
            item("description") { Text(chord.description, style = MaterialTheme.typography.bodyLarge) }
        }
        item("actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioButton(onClick = { onOpenChord(chord.symbol) }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开完整详情")
                }
                StudioOutlinedButton(onClick = { onToggleFavorite(chord.symbol) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (chord.favorite) "取消收藏" else "加入收藏")
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onFavorite: () -> Unit, onExport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "退出多选") }
        Text("已选择 $count 项", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onFavorite) { Icon(Icons.Rounded.Favorite, contentDescription = "收藏或取消收藏所选") }
        IconButton(onClick = onExport) { Icon(Icons.Rounded.IosShare, contentDescription = "导出所选") }
    }
}

@Composable
private fun LibraryEmptyState(
    segment: LibrarySegment,
    query: String,
    onBrowse: () -> Unit,
    onCreateCustom: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val title: String
    val message: String
    val action: @Composable () -> Unit
    when {
        query.isNotBlank() -> {
            title = "没有匹配结果"
            message = "保留了搜索词，你可以调整输入或清除筛选。"
            action = { StudioButton(onClick = onClearFilters) { Text("清除筛选") } }
        }
        segment == LibrarySegment.FAVORITES -> {
            title = "还没有收藏"
            message = "浏览和弦并点击心形按钮，常用和弦会出现在这里。"
            action = { StudioButton(onClick = onBrowse) { Text("浏览和弦") } }
        }
        segment == LibrarySegment.CUSTOM -> {
            title = "还没有自定义指法"
            message = "从交互指板识别和弦并保存自己的按法。"
            action = { StudioButton(onClick = onCreateCustom) { Text("识别并保存指法") } }
        }
        else -> {
            title = "这里暂时为空"
            message = "更改分段或筛选后再试。"
            action = { StudioButton(onClick = onClearFilters) { Text("重置") } }
        }
    }
    EmptyState(title, message, action = action)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    current: LibraryFilter,
    roots: List<String>,
    qualities: List<Pair<String, String>>,
    accidentalPreference: AccidentalPreference,
    onDismiss: () -> Unit,
    onAccidentalPreferenceChanged: (AccidentalPreference) -> Unit,
    onApply: (LibraryFilter) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("筛选和弦", style = MaterialTheme.typography.headlineLarge)
            Text("记谱", style = MaterialTheme.typography.titleMedium)
            StudioSegmentedControl(
                options = AccidentalPreference.entries.map { it to it.label },
                selected = accidentalPreference,
                onSelected = onAccidentalPreferenceChanged,
            )
            Text("类别", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item("all-families") {
                    FilterChip(
                        selected = draft.familyId.isEmpty(),
                        onClick = { draft = draft.copy(familyId = "", qualityId = "") },
                        label = { Text("全部") },
                    )
                }
                items(ChordFamily.entries, key = { it.id }) { family ->
                    FilterChip(
                        selected = draft.familyId == family.id,
                        onClick = { draft = draft.copy(familyId = family.id, qualityId = "") },
                        label = { Text(family.label) },
                    )
                }
            }
            Text("根音", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item("all") {
                    FilterChip(selected = draft.root.isEmpty(), onClick = { draft = draft.copy(root = "") }, label = { Text("全部") })
                }
                items(roots, key = { it }) { root ->
                    FilterChip(
                        selected = draft.root == root,
                        onClick = { draft = draft.copy(root = root) },
                        label = { Text(rootChoiceLabel(root)) },
                    )
                }
            }
            Text("具体类型", style = MaterialTheme.typography.titleMedium)
            val selectedFamily = ChordFamily.fromId(draft.familyId)
            if (selectedFamily == null) {
                Text(
                    "选择一个类别后可以继续细分；保持“全部”即浏览所有和弦性质。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AdaptiveControlGroup {
                    FilterChip(
                        selected = draft.qualityId.isEmpty(),
                        onClick = { draft = draft.copy(qualityId = "") },
                        label = { Text("${selectedFamily.label}全部") },
                    )
                    qualities.filter { selectedFamily.contains(it.first) }.forEach { quality ->
                        FilterChip(
                            selected = draft.qualityId == quality.first,
                            onClick = { draft = draft.copy(qualityId = quality.first) },
                            label = { Text(quality.second) },
                        )
                    }
                }
            }
            Text("难度", style = MaterialTheme.typography.titleMedium)
            AdaptiveControlGroup {
                listOf(0 to "全部", 1 to "入门", 2 to "中级", 3 to "进阶").forEach { option ->
                    FilterChip(
                        selected = draft.difficultyBucket == option.first,
                        onClick = { draft = draft.copy(difficultyBucket = option.first) },
                        label = { Text(option.second) },
                    )
                }
            }
            HorizontalDivider()
            FilterCheck("仅开放按法", draft.openOnly) { draft = draft.copy(openOnly = it) }
            FilterCheck("仅横按按法", draft.barreOnly) { draft = draft.copy(barreOnly = it) }
            FilterCheck("仅简化按法", draft.simplifiedOnly) { draft = draft.copy(simplifiedOnly = it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清除") }
                StudioButton(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("应用") }
            }
        }
    }
}

@Composable
private fun FilterCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

private fun activeFilterLabels(filter: LibraryFilter, qualities: List<Pair<String, String>>): List<String> = buildList {
    if (filter.familyId.isNotEmpty()) add(ChordFamily.fromId(filter.familyId)?.label ?: filter.familyId)
    if (filter.root.isNotEmpty()) add("根音 ${rootChoiceLabel(filter.root)}")
    if (filter.qualityId.isNotEmpty()) add(qualities.firstOrNull { it.first == filter.qualityId }?.second ?: filter.qualityId)
    if (filter.difficultyBucket > 0) add(listOf("", "入门", "中级", "进阶")[filter.difficultyBucket])
    if (filter.openOnly) add("开放")
    if (filter.barreOnly) add("横按")
    if (filter.simplifiedOnly) add("简化")
}
