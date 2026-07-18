package com.k2.music.ui.song

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.k2.music.ui.components.StudioOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.k2.music.ui.components.StudioTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.song.SongSection
import com.k2.music.song.SongTimingState
import com.k2.music.song.SongParseLineRole
import com.k2.music.MusicTheoryUtils
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.components.EmptyState
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeModeUi
import com.k2.music.ui.preferences.LocalExperienceCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SongLibraryRoute(
    services: CoreServices,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onManualCreate: () -> Unit,
    onOpenSong: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(SongLibraryViewModel::class) { SongLibraryViewModel(services.songGateway) }
    }
    val viewModel: SongLibraryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SongLibraryScreen(state, onBack, onImport, onManualCreate, onOpenSong, viewModel::setQuery, viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLibraryScreen(
    state: SongLibraryUiState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onManualCreate: () -> Unit,
    onOpenSong: (String) -> Unit,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("song_library_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text("本地曲谱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val data = state.data
        when {
            state.loading && data == null -> LoadingContent(Modifier.padding(padding))
            state.error != null && data == null -> EmptyState(
                title = "曲谱库暂时无法加载",
                message = state.error,
                modifier = Modifier.padding(padding),
                action = { StudioButton(onClick = onRetry) { Text("重试") } },
            )
            data != null && data.all.isEmpty() && state.query.isBlank() -> EmptyState(
                title = "还没有本地曲谱",
                message = "粘贴一份和弦谱，开始分段练习。",
                modifier = Modifier.padding(padding).testTag("song_library_empty"),
                icon = Icons.Rounded.LibraryMusic,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioButton(onClick = onImport) { Text("粘贴曲谱") }
                        StudioOutlinedButton(onClick = onManualCreate) { Text("手动创建") }
                    }
                },
            )
            data != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("search") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        modifier = Modifier.fillMaxWidth().testTag("song_search"),
                        label = { Text("搜索曲名、作者或原文") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                    )
                }
                item("actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("粘贴曲谱") }
                        StudioOutlinedButton(onClick = onManualCreate, modifier = Modifier.weight(1f)) { Text("手动创建") }
                    }
                }
                state.error?.let { message -> item("error") { InlineMessage(message, isError = true) } }
                if (state.query.isBlank() && data.recent.isNotEmpty()) {
                    item("recent_title") { SectionHeading("最近练习", "按最近一次真实练习排序") }
                    items(data.recent, key = { "recent-${it.project.id}" }) { SongCard(it, onOpenSong) }
                }
                if (state.query.isBlank() && data.incomplete.isNotEmpty()) {
                    item("incomplete_title") { SectionHeading("未完成设置", "缺少原调、可靠节奏或可练和弦") }
                    items(data.incomplete, key = { "incomplete-${it.project.id}" }) { SongCard(it, onOpenSong) }
                }
                item("all_title") { SectionHeading(if (state.query.isBlank()) "全部曲谱" else "搜索结果", "${data.all.size} 首") }
                if (data.all.isEmpty()) {
                    item("no_result") { InlineMessage("没有匹配的本地曲谱。") }
                } else {
                    items(data.all, key = { "all-${it.project.id}" }) { SongCard(it, onOpenSong) }
                }
            }
        }
    }
}

@Composable
private fun SongCard(card: SongCardUi, onOpenSong: (String) -> Unit) {
    Card(
        onClick = { onOpenSong(card.project.id) },
        modifier = Modifier.fillMaxWidth().testTag("song_card_${card.project.id}"),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(card.project.title, style = MaterialTheme.typography.titleMedium)
                    Text(card.project.artist.ifBlank { "作者未填写" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(card.practiceStatus, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text("${card.sectionCount} 个段落 · ${card.chordCount} 个和弦事件 · ${card.recentBpm?.let { "$it BPM" } ?: "尚无练习速度"}")
            Text(
                card.lastPracticeAt?.let { "上次练习 ${formatDate(it)}" } ?: "尚未练习",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SongImportRoute(
    services: CoreServices,
    onBack: () -> Unit,
    onPreview: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(SongImportViewModel::class) { handle -> SongImportViewModel(services.songGateway, handle) }
    }
    val viewModel: SongImportViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { onPreview() }
    }
    SongImportScreen(
        state,
        onBack,
        viewModel::setTitle,
        viewModel::setArtist,
        viewModel::setOriginalText,
        viewModel::setTimeSignature,
        viewModel::parse,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongImportScreen(
    state: SongImportUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onArtist: (String) -> Unit,
    onOriginalText: (String) -> Unit,
    onTimeSignature: (String) -> Unit,
    onParse: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("song_import_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text("粘贴曲谱") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    StudioButton(
                        onClick = onParse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .testTag("song_parse_button"),
                        enabled = !state.parsing,
                    ) {
                        if (state.parsing) {
                            CircularProgressIndicator(
                                Modifier.width(20.dp).height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("解析并预览")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("intro") { Text("原文会原样保留；解析只建立可编辑的段落与和弦事件。") }
            item("title") {
                OutlinedTextField(state.title, onTitle, Modifier.fillMaxWidth(), label = { Text("曲名") }, singleLine = true)
            }
            item("artist") {
                OutlinedTextField(state.artist, onArtist, Modifier.fillMaxWidth(), label = { Text("作者（可选）") }, singleLine = true)
            }
            item("signature") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("4/4", "3/4", "6/8").forEach { signature ->
                        FilterChip(
                            selected = state.timeSignature == signature,
                            onClick = { onTimeSignature(signature) },
                            label = { Text(signature) },
                        )
                    }
                }
            }
            item("text") {
                OutlinedTextField(
                    value = state.originalText,
                    onValueChange = onOriginalText,
                    modifier = Modifier.fillMaxWidth().height(300.dp).testTag("song_import_text"),
                    label = { Text("原始文本") },
                    placeholder = { Text("[主歌]\nC        G\n一句歌词\n| Am | F |") },
                )
            }
            item("example") {
                InlineMessage("支持 [C]歌词、和弦行 + 歌词行，以及 | C G | Am F | 小节格式。无法识别的 Token 会在预览中列出。")
            }
            state.message?.let { item("message") { InlineMessage(it, isError = true) } }
        }
    }
}

@Composable
fun SongImportPreviewRoute(
    services: CoreServices,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(SongPreviewViewModel::class) { SongPreviewViewModel(services.songGateway) }
    }
    val viewModel: SongPreviewViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SongPreviewEffect.Saved -> onSaved(effect.songId)
            }
        }
    }
    SongImportPreviewScreen(state, onBack, onBack, viewModel::save, viewModel::load, viewModel::setLineRole)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongImportPreviewScreen(
    state: SongPreviewUiState,
    onBack: () -> Unit,
    onCorrect: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onLineRole: (Int, SongParseLineRole) -> Unit,
) {
    var showLineTools by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("song_import_preview_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text("解析预览") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onSave, enabled = state.draft != null && !state.saving) {
                        Icon(Icons.Rounded.Save, contentDescription = "保存曲谱")
                    }
                },
            )
        },
    ) { padding ->
        val draft = state.draft
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            draft == null -> EmptyState(
                title = "无法显示预览",
                message = state.error ?: "导入草稿已失效。",
                modifier = Modifier.padding(padding),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioOutlinedButton(onClick = onCorrect) { Text("返回修改原文") }
                        StudioButton(onClick = onRetry) { Text("重试") }
                    }
                },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("song_import_preview_list"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("title") {
                    Text(draft.title, style = MaterialTheme.typography.headlineSmall)
                    if (draft.artist.isNotBlank()) Text(draft.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item("stats") {
                    AdaptiveStatGrid(
                        listOf(
                            AdaptiveStat("段落", draft.parseResult.sections.size.toString()),
                            AdaptiveStat("和弦事件", draft.parseResult.chordEventCount.toString()),
                            AdaptiveStat("有效和弦", draft.parseResult.validChords.size.toString()),
                            AdaptiveStat("置信度", "%.0f%%".format(draft.parseResult.confidence * 100)),
                            AdaptiveStat("可以练习", if (draft.parseResult.canStartPractice) "是" else "需要修正"),
                        ),
                    )
                }
                item("timing") {
                    InlineMessage(
                        when (draft.parseResult.timingState) {
                            SongTimingState.UNTYPED -> "当前没有可靠拍数，只启用手动滚动与手动段落选择。"
                            SongTimingState.SIMPLE_MEASURES -> "已按小节规则推断拍数，保存后可在编辑器中逐项修改。"
                            SongTimingState.EXPLICIT_BEATS -> "所有和弦都有明确拍数。"
                        },
                    )
                }
                if (draft.parseResult.unrecognizedTokens.isNotEmpty()) {
                    item("unknown") {
                        InlineMessage(
                            "未识别 Token：${draft.parseResult.unrecognizedTokens.joinToString("、")}",
                            isError = true,
                        )
                    }
                }
                if (draft.parseResult.warnings.isNotEmpty()) {
                    item("warnings") { SectionHeading("警告", "请在保存前确认") }
                    items(draft.parseResult.warnings) { warning ->
                        Text("• ${warning.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                item("correction_toggle") {
                    StudioOutlinedButton(
                        onClick = { showLineTools = !showLineTools },
                        modifier = Modifier.fillMaxWidth().testTag("song_line_correction_toggle"),
                    ) {
                        Text(if (showLineTools) "收起逐行修正" else "逐行标记和弦、歌词、标题或普通文本")
                    }
                }
                if (showLineTools) {
                    val correctionLines = draft.originalText
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .split('\n')
                        .mapIndexedNotNull { index, line -> (index + 1 to line).takeIf { line.isNotBlank() } }
                    items(correctionLines, key = { it.first }) { (lineNumber, line) ->
                        Card(border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("第 $lineNumber 行", style = MaterialTheme.typography.labelLarge)
                                Text(line)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(
                                        listOf(
                                            SongParseLineRole.AUTO to "自动",
                                            SongParseLineRole.CHORDS to "和弦",
                                            SongParseLineRole.LYRICS to "歌词",
                                            SongParseLineRole.SECTION_TITLE to "段落标题",
                                            SongParseLineRole.TEXT to "普通文本",
                                        ),
                                        key = { it.first.name },
                                    ) { (role, label) ->
                                        FilterChip(
                                            selected = (draft.lineOverrides[lineNumber] ?: SongParseLineRole.AUTO) == role,
                                            onClick = { onLineRole(lineNumber, role) },
                                            enabled = state.correctingLine == null,
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                if (state.correctingLine == lineNumber) Text("正在重新解析这一行…")
                            }
                        }
                    }
                }
                item("sections_title") { SectionHeading("段落预览", "可在编辑器中继续修正") }
                items(draft.parseResult.sections, key = { it.id }) { section -> SongSectionPreview(section) }
                item("actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioButton(onClick = onSave, enabled = !state.saving && state.correctingLine == null, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.saving) "正在保存…" else "保存曲谱")
                        }
                        StudioOutlinedButton(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) { Text("返回修改原文") }
                    }
                }
                state.error?.let { item("error") { InlineMessage(it, isError = true) } }
            }
        }
    }
}

@Composable
fun SongDetailRoute(
    services: CoreServices,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onGuidedPractice: (PracticeConfigUi) -> Unit,
    onPerformance: (String, String?) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(SongDetailViewModel::class) { handle -> SongDetailViewModel(services.songGateway, handle) }
    }
    val viewModel: SongDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { onBack() }
    }
    SongDetailScreen(
        state,
        onBack,
        onEdit,
        onGuidedPractice,
        onPerformance,
        viewModel::delete,
        viewModel::refresh,
        viewModel::setTranspose,
        viewModel::setCapo,
        viewModel::setAccidentalPreference,
        viewModel::resetArrangement,
        viewModel::pinVoicing,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    state: SongDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onGuidedPractice: (PracticeConfigUi) -> Unit,
    onPerformance: (String, String?) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onTranspose: (Int) -> Unit,
    onCapo: (Int) -> Unit,
    onAccidentalPreference: (MusicTheoryUtils.AccidentalPreference) -> Unit,
    onResetArrangement: () -> Unit,
    onPinVoicing: (String, String?) -> Unit,
) {
    val capabilities = LocalExperienceCapabilities.current
    var confirmDelete by remember { mutableStateOf(false) }
    var guidedTarget by remember { mutableStateOf<String?>(null) }
    var showAllVoicings by rememberSaveable(capabilities.showAllVoicingsByDefault) {
        mutableStateOf(capabilities.showAllVoicingsByDefault)
    }
    val data = state.data
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("song_detail_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text(data?.project?.title ?: "曲谱详情") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { data?.project?.id?.let(onEdit) }, enabled = data != null) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑曲谱")
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = data != null && !state.deleting) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除曲谱")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && data == null -> LoadingContent(Modifier.padding(padding))
            data == null -> EmptyState(
                title = "曲谱不可用",
                message = state.error ?: "这份曲谱不存在。",
                modifier = Modifier.padding(padding),
                action = { StudioButton(onClick = onRetry) { Text("重试") } },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("song_detail_list"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("artist") {
                    Text(data.project.artist.ifBlank { "作者未填写" }, style = MaterialTheme.typography.titleMedium)
                }
                item("config") {
                    val arrangement = state.arrangement
                    AdaptiveStatGrid(
                        if (capabilities.showSongTheoryFields) {
                            listOf(
                                AdaptiveStat("原调", data.project.originalKey.ifBlank { "未设置" }),
                                AdaptiveStat("实际听到的调", arrangement?.soundingKey ?: "计算中"),
                                AdaptiveStat("当前手型调", arrangement?.shapeKey ?: "计算中"),
                                AdaptiveStat("移调", "%+d 半音".format(data.project.transposeSemitones)),
                                AdaptiveStat("变调夹", "${data.project.capoFret} 品"),
                                AdaptiveStat("速度", "${data.project.bpm} BPM"),
                                AdaptiveStat("拍号", data.project.timeSignature),
                                AdaptiveStat("横按和弦", "按本地指法方案统计"),
                                AdaptiveStat("曲谱难度", data.difficultyLabel),
                            )
                        } else {
                            listOf(
                                AdaptiveStat("当前手型", arrangement?.shapeKey ?: "计算中"),
                                AdaptiveStat("变调夹", "${data.project.capoFret} 品"),
                                AdaptiveStat("练习速度", "${data.project.bpm} BPM"),
                                AdaptiveStat("曲谱难度", data.difficultyLabel),
                            )
                        },
                    )
                }
                item("progress") {
                    val progress = data.progress
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeading("曲谱成长看板", "全部为本机真实练习记录")
                        AdaptiveStatGrid(
                            listOf(
                                AdaptiveStat("总练习时间", formatSongDuration(progress.totalPracticeSeconds)),
                                AdaptiveStat("最近 7 天", formatSongDuration(progress.sevenDayPracticeSeconds)),
                                AdaptiveStat("最近 BPM", progress.recentBpm?.toString() ?: "暂无"),
                                AdaptiveStat("最高完成 BPM", progress.highestCompletedBpm?.toString() ?: "暂无"),
                                AdaptiveStat("待解决困难", progress.unresolvedDifficultyCount.toString()),
                                AdaptiveStat("已解决困难", progress.resolvedDifficultyCount.toString()),
                                AdaptiveStat("未练习段落", progress.unpracticedSectionCount.toString()),
                                AdaptiveStat("完整演奏次数", progress.completionCount.toString()),
                                AdaptiveStat(
                                    "最近完整演奏",
                                    progress.lastCompletePerformanceAt?.let(::formatDate) ?: "暂无",
                                ),
                            ),
                        )
                        if (progress.sectionPracticeCounts.isNotEmpty()) {
                            Text(
                                "段落练习次数：" + progress.sectionPracticeCounts.entries.joinToString(" · ") { (name, count) ->
                                    "$name $count 次"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "连续演奏只记录实际时长、配置与用户确认的完成状态，不计算成功率。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("practice") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioButton(
                            onClick = { onPerformance(data.project.id, null) },
                            enabled = data.project.chordEventCount >= 2,
                            modifier = Modifier.weight(1f).testTag("song_performance_whole"),
                        ) { Text("连续演奏") }
                        StudioOutlinedButton(
                            onClick = { guidedTarget = WHOLE_SONG_GUIDED_TARGET },
                            enabled = data.project.chordEventCount >= 2,
                            modifier = Modifier.weight(1f).testTag("song_guided_whole"),
                        ) { Text("专项切换") }
                    }
                }
                item("arrangement_controls") {
                    val project = data.project
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeading("移调与变调夹", if (state.arranging) "正在保存…" else "离线确定性计算")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("整体移调", Modifier.weight(1f))
                                TextButton(onClick = { onTranspose(project.transposeSemitones - 1) }, enabled = !state.arranging, modifier = Modifier.testTag("song_transpose_down")) { Text("−") }
                                Text("%+d".format(project.transposeSemitones), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onTranspose(project.transposeSemitones + 1) }, enabled = !state.arranging, modifier = Modifier.testTag("song_transpose_up")) { Text("+") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("变调夹位置", Modifier.weight(1f))
                                TextButton(onClick = { onCapo(project.capoFret - 1) }, enabled = !state.arranging, modifier = Modifier.testTag("song_capo_down")) { Text("−") }
                                Text("${project.capoFret} 品", fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onCapo(project.capoFret + 1) }, enabled = !state.arranging, modifier = Modifier.testTag("song_capo_up")) { Text("+") }
                            }
                            Text("升降号偏好", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    MusicTheoryUtils.AccidentalPreference.AUTO to "自动",
                                    MusicTheoryUtils.AccidentalPreference.SHARPS to "升号",
                                    MusicTheoryUtils.AccidentalPreference.FLATS to "降号",
                                ).forEach { (preference, label) ->
                                    FilterChip(
                                        selected = project.accidentalPreference == preference,
                                        onClick = { onAccidentalPreference(preference) },
                                        enabled = !state.arranging,
                                        label = { Text(label) },
                                    )
                                }
                            }
                            StudioOutlinedButton(onClick = onResetArrangement, enabled = !state.arranging, modifier = Modifier.fillMaxWidth()) {
                                Text("一键恢复原调与无变调夹")
                            }
                        }
                    }
                }
                state.arrangement?.let { arrangement ->
                    if (arrangement.capoPlans.isNotEmpty()) {
                        item("capo_plans_title") { SectionHeading("变调夹推荐", "最多 3 个本地方案") }
                        items(arrangement.capoPlans.take(capabilities.songCapoPlanLimit), key = { "capo-${it.capoFret}" }) { plan ->
                            Card(border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("变调夹 ${plan.capoFret} 品", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { onCapo(plan.capoFret) }, enabled = !state.arranging) { Text("采用") }
                                    }
                                    Text("手型：${plan.shapes.joinToString(" · ")}")
                                    Text("横按 ${plan.barreChordCount} · 不熟悉 ${plan.unfamiliarChordCount} · 最高 ${plan.highestFret} 品")
                                    Text(plan.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (arrangement.warnings.isNotEmpty()) {
                        item("arrangement_warning") { InlineMessage(arrangement.warnings.joinToString("\n"), isError = true) }
                    }
                    if (arrangement.renderedChords.isNotEmpty()) {
                        val visibleRenderedChords = if (showAllVoicings) {
                            arrangement.renderedChords
                        } else {
                            arrangement.renderedChords.take(capabilities.songVoicingPreviewLimit)
                        }
                        item("voicings_title") {
                            SectionHeading("曲谱固定指法", "逐个和弦事件保存；失效时自动回退")
                        }
                        items(visibleRenderedChords, key = { "voicing-${it.eventId}" }) { rendered ->
                            Card(border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "原谱 ${rendered.sourceChord} · 实际 ${rendered.soundingChord} · 手型 ${rendered.shapeChord}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    rendered.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item("auto") {
                                            FilterChip(
                                                selected = rendered.availableVoicings.none { it.pinned },
                                                onClick = { onPinVoicing(rendered.eventId, null) },
                                                enabled = !state.arranging,
                                                label = { Text("自动推荐") },
                                            )
                                        }
                                        items(rendered.availableVoicings, key = { it.id }) { choice ->
                                            FilterChip(
                                                selected = choice.pinned,
                                                onClick = { onPinVoicing(rendered.eventId, choice.id) },
                                                enabled = !state.arranging,
                                                label = {
                                                    Text(
                                                        choice.name + when {
                                                            choice.familiar -> " · 已熟悉"
                                                            choice.barre -> " · 横按"
                                                            else -> ""
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (visibleRenderedChords.size < arrangement.renderedChords.size) {
                            item("voicings_more") {
                                StudioOutlinedButton(onClick = { showAllVoicings = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("显示全部 ${arrangement.renderedChords.size} 个和弦事件")
                                }
                            }
                        }
                    }
                }
                if (data.difficulties.isNotEmpty()) {
                    item("difficult_title") { SectionHeading("困难切换", "用户主动标记，不计为失败") }
                    item("difficult") {
                        Text(data.difficulties.joinToString(" · ") { "${it.fromChord} → ${it.toChord}" })
                    }
                }
                item("unpracticed") {
                    Text("未练习段落：${data.unpracticedSectionIds.size} 个；最近练习：${data.lastRun?.let { formatDate(it.startedAt) } ?: "暂无"}")
                }
                item("sections_title") { SectionHeading("段落列表", "${data.project.sections.size} 个") }
                items(data.project.sections, key = { it.id }) { section ->
                    Card(border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(section.name, style = MaterialTheme.typography.titleMedium)
                            Text("${section.rows.size} 行 · ${section.rows.sumOf { it.chordEvents.size }} 个和弦事件 · 重复 ${section.repeatCount} 次")
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onEdit(data.project.id) }) { Text("查看 / 编辑") }
                                TextButton(onClick = { guidedTarget = section.id }) { Text("循环 / 专项") }
                                TextButton(onClick = { onPerformance(data.project.id, section.id) }) { Text("连续演奏") }
                            }
                        }
                    }
                }
                state.error?.let { item("error") { InlineMessage(it, isError = true) } }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这份曲谱？") },
            text = { Text("曲谱、曲谱练习记录和用户困难标记都会从本机删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    guidedTarget?.let { target ->
        val sectionId = target.takeUnless { it == WHOLE_SONG_GUIDED_TARGET }
        val project = data?.project
        val transitions = data?.transitionsBySection?.get(sectionId).orEmpty()
        if (project != null) {
            AlertDialog(
                onDismissRequest = { guidedTarget = null },
                title = { Text("选择专项切换") },
                text = {
                    Column(
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("可练所选范围的完整切换顺序，也可只循环一对方向性切换。")
                        TextButton(
                            onClick = {
                                guidedTarget = null
                                onGuidedPractice(
                                    PracticeConfigUi(
                                        mode = PracticeModeUi.MULTI_CHORD,
                                        symbols = transitions.flatMap { listOf(it.fromChord, it.toChord) }.distinct().joinToString(" "),
                                        durationSeconds = 120,
                                        bpm = project.bpm,
                                        timeSignature = project.timeSignature,
                                        songId = project.id,
                                        songSectionId = sectionId.orEmpty(),
                                        useProgressionRhythm = true,
                                    ),
                                )
                            },
                            enabled = transitions.isNotEmpty(),
                        ) { Text("练完整段落切换") }
                        transitions.forEachIndexed { transitionIndex, transition ->
                            TextButton(
                                onClick = {
                                    guidedTarget = null
                                    onGuidedPractice(
                                        PracticeConfigUi(
                                            mode = PracticeModeUi.TWO_CHORD,
                                            symbols = "${transition.fromChord} ${transition.toChord}",
                                            durationSeconds = 60,
                                            bpm = project.bpm,
                                            timeSignature = project.timeSignature,
                                            songId = project.id,
                                            songSectionId = sectionId.orEmpty(),
                                            songTransitionFrom = transition.fromChord,
                                            songTransitionTo = transition.toChord,
                                        ),
                                    )
                                },
                                modifier = Modifier.testTag("song_guided_pair_$transitionIndex"),
                            ) { Text("${transition.fromChord} → ${transition.toChord}") }
                        }
                        if (transitions.isEmpty()) Text("当前范围没有可练的方向性切换。")
                    }
                },
                confirmButton = { TextButton(onClick = { guidedTarget = null }) { Text("取消") } },
            )
        }
    }
}

@Composable
fun SongEditorRoute(
    services: CoreServices,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(SongEditorViewModel::class) { handle -> SongEditorViewModel(services.songGateway, handle) }
    }
    val viewModel: SongEditorViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SongEditorEffect.Saved -> onSaved(effect.songId)
            }
        }
    }
    SongEditorScreen(
        state = state,
        onBack = onBack,
        onTitle = viewModel::setTitle,
        onArtist = viewModel::setArtist,
        onOriginalText = viewModel::setOriginalText,
        onOriginalKey = viewModel::setOriginalKey,
        onBpm = viewModel::setBpm,
        onTimeSignature = viewModel::setTimeSignature,
        onNotes = viewModel::setNotes,
        onReparse = viewModel::reparse,
        onSectionName = viewModel::setSectionName,
        onSectionRepeat = viewModel::setSectionRepeat,
        onMoveSection = viewModel::moveSection,
        onDeleteSection = viewModel::deleteSection,
        onDuration = viewModel::setEventDuration,
        onVoicing = viewModel::setEventVoicing,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongEditorScreen(
    state: SongEditorUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onArtist: (String) -> Unit,
    onOriginalText: (String) -> Unit,
    onOriginalKey: (String) -> Unit,
    onBpm: (String) -> Unit,
    onTimeSignature: (String) -> Unit,
    onNotes: (String) -> Unit,
    onReparse: () -> Unit,
    onSectionName: (Int, String) -> Unit,
    onSectionRepeat: (Int, Int) -> Unit,
    onMoveSection: (Int, Int) -> Unit,
    onDeleteSection: (Int) -> Unit,
    onDuration: (Int, Int, Int, String) -> Unit,
    onVoicing: (Int, Int, Int, String) -> Unit,
    onSave: () -> Unit,
) {
    val capabilities = LocalExperienceCapabilities.current
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    var pendingSectionDelete by remember { mutableStateOf<Int?>(null) }
    var advancedRhythm by rememberSaveable(capabilities.expandAdvancedSongRhythmEditor) {
        mutableStateOf(capabilities.expandAdvancedSongRhythmEditor)
    }
    fun requestBack() {
        if (state.dirty) confirmDiscard = true else onBack()
    }
    BackHandler(onBack = ::requestBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("song_editor_screen"),
        topBar = {
            StudioTopAppBar(
                title = {
                    Column {
                        Text("曲谱编辑")
                        Text(if (state.dirty) "有未保存修改" else "已保存", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { BackButton(::requestBack) },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.project?.title == "未命名曲谱" && state.project.createdAt == state.project.updatedAt) onSave()
                            else confirmOverwrite = true
                        },
                        enabled = state.project != null && !state.saving,
                    ) { Icon(Icons.Rounded.Save, contentDescription = "保存曲谱") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            state.project == null -> EmptyState("无法编辑曲谱", state.error ?: "曲谱不存在。", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("song_editor_list"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { item("error") { InlineMessage(it, isError = true) } }
                item("title") { OutlinedTextField(state.title, onTitle, Modifier.fillMaxWidth(), label = { Text("曲名") }, singleLine = true) }
                item("artist") { OutlinedTextField(state.artist, onArtist, Modifier.fillMaxWidth(), label = { Text("作者") }, singleLine = true) }
                item("config") {
                    BoxWithConstraints {
                        if (maxWidth >= 600.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(state.originalKey, onOriginalKey, Modifier.weight(1f), label = { Text("原调") }, singleLine = true)
                                OutlinedTextField(state.bpmText, onBpm, Modifier.weight(1f).testTag("song_editor_bpm"), label = { Text("BPM") }, singleLine = true)
                                OutlinedTextField(state.timeSignature, onTimeSignature, Modifier.weight(1f), label = { Text("拍号") }, singleLine = true)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(state.originalKey, onOriginalKey, Modifier.fillMaxWidth(), label = { Text("原调") }, singleLine = true)
                                OutlinedTextField(state.bpmText, onBpm, Modifier.fillMaxWidth().testTag("song_editor_bpm"), label = { Text("BPM") }, singleLine = true)
                                OutlinedTextField(state.timeSignature, onTimeSignature, Modifier.fillMaxWidth(), label = { Text("拍号") }, singleLine = true)
                            }
                        }
                    }
                }
                item("raw_title") { SectionHeading("歌词与和弦原文", "修改后点击重新解析，推断结果仍可逐项调整") }
                item("raw") {
                    OutlinedTextField(
                        state.originalText,
                        onOriginalText,
                        Modifier.fillMaxWidth().height(240.dp),
                        label = { Text("曲谱原文") },
                    )
                }
                item("reparse") {
                    StudioOutlinedButton(onClick = onReparse, enabled = !state.reparsing, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.reparsing) "正在重新解析…" else "重新解析歌词与和弦")
                    }
                }
                item("sections_title") { SectionHeading("结构化段落", "名称、顺序、重复、持续拍数与固定指法") }
                item("advanced_rhythm") {
                    StudioOutlinedButton(onClick = { advancedRhythm = !advancedRhythm }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (advancedRhythm) "收起高级拍数与固定指法" else "展开高级拍数与固定指法")
                    }
                }
                items(state.sections.size, key = { state.sections[it].id }) { sectionIndex ->
                    val section = state.sections[sectionIndex]
                    EditorSectionCard(
                        section = section,
                        sectionIndex = sectionIndex,
                        onName = onSectionName,
                        onRepeat = onSectionRepeat,
                        onMove = onMoveSection,
                        onDelete = { pendingSectionDelete = it },
                        onDuration = onDuration,
                        onVoicing = onVoicing,
                        showAdvancedRhythm = advancedRhythm,
                    )
                }
                item("notes") {
                    OutlinedTextField(state.notes, onNotes, Modifier.fillMaxWidth().height(120.dp), label = { Text("备注") })
                }
                item("save") {
                    StudioButton(
                        onClick = { confirmOverwrite = true },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.saving) "正在保存…" else "保存曲谱") }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("离开后，本次尚未保存的曲谱修改会丢失。") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("放弃并离开") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
        )
    }
    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("保存并覆盖当前版本？") },
            text = { Text("原始导入文本会保留为当前编辑内容，结构化段落将写入本地曲谱。") },
            confirmButton = { TextButton(onClick = { confirmOverwrite = false; onSave() }) { Text("确认保存") } },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("取消") } },
        )
    }
    pendingSectionDelete?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingSectionDelete = null },
            title = { Text("删除这个段落？") },
            text = { Text("段落内的歌词、和弦、拍数与固定指法都会被删除。") },
            confirmButton = {
                TextButton(onClick = { pendingSectionDelete = null; onDeleteSection(index) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingSectionDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EditorSectionCard(
    section: SongSection,
    sectionIndex: Int,
    onName: (Int, String) -> Unit,
    onRepeat: (Int, Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDuration: (Int, Int, Int, String) -> Unit,
    onVoicing: (Int, Int, Int, String) -> Unit,
    showAdvancedRhythm: Boolean,
) {
    Card(border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    section.name,
                    { onName(sectionIndex, it) },
                    Modifier.weight(1f),
                    label = { Text("段落名称") },
                    singleLine = true,
                )
                IconButton(onClick = { onMove(sectionIndex, -1) }) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上移段落")
                }
                IconButton(onClick = { onMove(sectionIndex, 1) }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下移段落")
                }
                IconButton(onClick = { onDelete(sectionIndex) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除段落")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("重复 ${section.repeatCount} 次", Modifier.weight(1f))
                TextButton(onClick = { onRepeat(sectionIndex, section.repeatCount - 1) }) { Text("−") }
                TextButton(onClick = { onRepeat(sectionIndex, section.repeatCount + 1) }) { Text("+") }
            }
            section.rows.forEachIndexed { rowIndex, row ->
                if (row.rawChordText.isNotBlank()) Text(row.rawChordText, fontWeight = FontWeight.SemiBold)
                if (row.lyricText.isNotBlank()) Text(row.lyricText)
                if (showAdvancedRhythm) {
                    row.chordEvents.forEachIndexed { eventIndex, event ->
                        BoxWithConstraints {
                            if (maxWidth >= 600.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(event.chordSymbol, Modifier.width(64.dp).padding(top = 16.dp), fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        event.durationBeats?.toString().orEmpty(),
                                        { onDuration(sectionIndex, rowIndex, eventIndex, it) },
                                        Modifier.weight(0.8f), label = { Text("持续拍数") }, singleLine = true,
                                    )
                                    OutlinedTextField(
                                        event.selectedVoicingId.orEmpty(),
                                        { onVoicing(sectionIndex, rowIndex, eventIndex, it) },
                                        Modifier.weight(1.2f), label = { Text("固定指法 ID") }, singleLine = true,
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(event.chordSymbol, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        event.durationBeats?.toString().orEmpty(),
                                        { onDuration(sectionIndex, rowIndex, eventIndex, it) },
                                        Modifier.fillMaxWidth(), label = { Text("持续拍数") }, singleLine = true,
                                    )
                                    OutlinedTextField(
                                        event.selectedVoicingId.orEmpty(),
                                        { onVoicing(sectionIndex, rowIndex, eventIndex, it) },
                                        Modifier.fillMaxWidth(), label = { Text("固定指法 ID") }, singleLine = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongSectionPreview(section: SongSection) {
    Card(border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(section.name, style = MaterialTheme.typography.titleMedium)
            section.rows.take(6).forEach { row ->
                if (row.rawChordText.isNotBlank()) Text(row.rawChordText, fontWeight = FontWeight.SemiBold)
                if (row.lyricText.isNotBlank()) Text(row.lyricText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (section.rows.size > 6) Text("还有 ${section.rows.size - 6} 行…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("正在读取本地曲谱…")
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatSongDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val remainingSeconds = safe % 60
    return when {
        hours > 0 -> "${hours} 小时 ${minutes} 分"
        minutes > 0 -> "${minutes} 分 ${remainingSeconds} 秒"
        else -> "${remainingSeconds} 秒"
    }
}

private const val WHOLE_SONG_GUIDED_TARGET = "__whole_song__"
