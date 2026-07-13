package com.k2.music.ui.recognition

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.RecognitionMatchUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RecognitionRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenChord: (String) -> Unit,
    onPractice: (String) -> Unit,
    onAddProgression: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(RecognitionViewModel::class) { handle ->
            RecognitionViewModel(
                services.recognitionGateway,
                services.chordCatalogGateway,
                services.playbackController,
                services.userLibraryGateway,
                handle,
            )
        }
    }
    val viewModel: RecognitionViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                RecognitionEffect.Cleared -> {
                    val result = snackbarHostState.showSnackbar("已清空指板", actionLabel = "撤销")
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undoClear()
                }
                is RecognitionEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is RecognitionEffect.Saved -> snackbarHostState.showSnackbar("已保存 ${effect.symbol} 自定义指法")
                is RecognitionEffect.FavoriteChanged -> snackbarHostState.showSnackbar(
                    if (effect.favorite) "已收藏 ${effect.symbol}" else "已取消收藏 ${effect.symbol}",
                )
            }
        }
    }
    RecognitionScreen(
        state = state,
        onBack = onBack,
        onModeChange = viewModel::setInputMode,
        onToolChange = viewModel::setInputTool,
        onStartFretChange = viewModel::setStartFret,
        onFretTap = viewModel::handleFretTap,
        onCycleString = viewModel::cycleString,
        onClear = viewModel::clear,
        onNotesChange = viewModel::updateNotes,
        onIdentify = viewModel::identifyNow,
        onPlay = viewModel::playCandidate,
        onFavorite = viewModel::toggleFavorite,
        onOpenChord = { onOpenChord(it.symbol) },
        onPractice = { onPractice(it.symbol) },
        onAddProgression = { onAddProgression(it.symbol) },
        onSave = viewModel::saveCustom,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    state: RecognitionUiState,
    onBack: () -> Unit,
    onModeChange: (RecognitionInputMode) -> Unit,
    onToolChange: (FretInputTool) -> Unit,
    onStartFretChange: (Int) -> Unit,
    onFretTap: (Int, Int) -> Unit,
    onCycleString: (Int) -> Unit,
    onClear: () -> Unit,
    onNotesChange: (String) -> Unit,
    onIdentify: () -> Unit,
    onPlay: (RecognitionMatchUi) -> Unit,
    onFavorite: (RecognitionMatchUi) -> Unit,
    onOpenChord: (RecognitionMatchUi) -> Unit,
    onPractice: (RecognitionMatchUi) -> Unit,
    onAddProgression: (RecognitionMatchUi) -> Unit,
    onSave: (RecognitionMatchUi, String, String, String, Int, String) -> Unit,
) {
    var saveMatch by remember { mutableStateOf<RecognitionMatchUi?>(null) }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("recognition_screen"),
        topBar = {
            TopAppBar(
                title = { Text("反向识别") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("mode") {
                AdaptiveControlGroup {
                    RecognitionInputMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.inputMode == mode,
                            onClick = { onModeChange(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
            if (state.inputMode == RecognitionInputMode.FRETBOARD) {
                item("tools") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FretInputTool.entries, key = { it.name }) { tool ->
                            FilterChip(
                                selected = state.inputTool == tool,
                                onClick = { onToolChange(tool) },
                                label = { Text(tool.label) },
                            )
                        }
                    }
                }
                item("range") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onStartFretChange(state.startFret - 1) },
                            enabled = state.startFret > 1,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "显示更低的五个品位") }
                        Text(
                            "显示 ${state.startFret}–${state.startFret + 4} 品",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onStartFretChange(state.startFret + 1) },
                            enabled = state.startFret < 26,
                        ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "显示更高的五个品位") }
                        IconButton(onClick = onClear) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "清空指板")
                        }
                    }
                }
                item("board") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        EditableFretboardCanvas(
                            frets = state.frets,
                            startFret = state.startFret,
                            tool = state.inputTool,
                            onTap = onFretTap,
                            modifier = Modifier.fillMaxWidth().height(330.dp).padding(16.dp),
                        )
                    }
                }
                item("string-alternatives") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(6, key = { it }) { index ->
                            AssistChip(
                                onClick = { onCycleString(index) },
                                label = { Text("${6 - index}弦 ${fretLabel(state.frets[index])}") },
                            )
                        }
                    }
                }
            } else {
                item("note-input") {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = onNotesChange,
                        modifier = Modifier.fillMaxWidth().testTag("recognition_note_input"),
                        label = { Text("按最低音到最高音输入") },
                        placeholder = { Text("例如 C E G，或 E G C") },
                        minLines = 3,
                    )
                }
            }
            item("identify") {
                Button(onClick = onIdentify, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (state.calculating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("识别和弦")
                }
            }
            state.error?.let { error -> item("error") { InlineMessage(error, isError = true) } }
            if (state.matches.isNotEmpty()) {
                item("candidate-title") {
                    Text("候选结果", style = MaterialTheme.typography.titleLarge)
                }
                items(state.matches, key = { "${it.symbol}:${it.score}" }) { match ->
                    CandidateCard(
                        match = match,
                        canSave = state.inputMode == RecognitionInputMode.FRETBOARD,
                        favorite = match.symbol in state.favoriteSymbols,
                        onPlay = { onPlay(match) },
                        onFavorite = { onFavorite(match) },
                        onOpen = { onOpenChord(match) },
                        onPractice = { onPractice(match) },
                        onAddProgression = { onAddProgression(match) },
                        onSave = { saveMatch = match },
                    )
                }
            }
        }
    }
    saveMatch?.let { match ->
        SaveCustomSheet(
            match = match,
            onDismiss = { saveMatch = null },
            initialStartFret = state.startFret,
            onSave = { symbol, name, fingers, startFret, note ->
                onSave(match, symbol, name, fingers, startFret, note)
                saveMatch = null
            },
        )
    }
}

@Composable
private fun CandidateCard(
    match: RecognitionMatchUi,
    canSave: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onOpen: () -> Unit,
    onPractice: () -> Unit,
    onAddProgression: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(match.symbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${match.score}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text("${match.chineseName} · ${match.matchLabel}", style = MaterialTheme.typography.bodyLarge)
            Text("组成音：${match.chordNotes.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium)
            Text("实际音：${match.actualNotes.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium)
            if (match.missingNotes.isNotEmpty()) Text("缺少：${match.missingNotes.joinToString(" · ")}")
            if (match.extraNotes.isNotEmpty()) Text("额外：${match.extraNotes.joinToString(" · ")}")
            if (match.inversion) Text("转位：最低音为 ${match.bassNote}")
            AdaptiveControlGroup(Modifier.fillMaxWidth()) {
                TextButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text("试听")
                }
                TextButton(onClick = onOpen) { Text("详情") }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "取消收藏 ${match.symbol}" else "收藏 ${match.symbol}",
                    )
                }
                if (canSave) {
                    TextButton(onClick = onSave) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Text("保存")
                    }
                }
                TextButton(onClick = onPractice) { Text("用它练习") }
                TextButton(onClick = onAddProgression) { Text("加入进行") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveCustomSheet(
    match: RecognitionMatchUi,
    initialStartFret: Int,
    onDismiss: () -> Unit,
    onSave: (symbol: String, name: String, fingers: String, startFret: Int, note: String) -> Unit,
) {
    var symbol by remember(match) { mutableStateOf(match.symbol) }
    var name by remember(match) { mutableStateOf("${match.symbol} 自定义指法") }
    var fingers by remember(match) { mutableStateOf("") }
    var startFret by remember(match, initialStartFret) { mutableIntStateOf(initialStartFret.coerceIn(1, 26)) }
    var note by remember(match) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("保存自定义指法", style = MaterialTheme.typography.headlineLarge)
            OutlinedTextField(symbol, { symbol = it }, Modifier.fillMaxWidth(), label = { Text("所属和弦") }, singleLine = true)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("指法名称") }, singleLine = true)
            OutlinedTextField(
                fingers,
                { fingers = it },
                Modifier.fillMaxWidth(),
                label = { Text("手指编号（可选）") },
                supportingText = { Text("六个 0–4 数字，例如 0 3 2 0 1 0") },
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("起始品位：$startFret", modifier = Modifier.weight(1f))
                IconButton(onClick = { startFret = (startFret - 1).coerceAtLeast(1) }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "起始品位减一")
                }
                IconButton(onClick = { startFret = (startFret + 1).coerceAtMost(26) }) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "起始品位加一")
                }
            }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onSave(symbol, name, fingers, startFret, note) }, modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }
    }
}

private fun fretLabel(value: Int): String = when (value) {
    UNSET_FRET -> "未设"
    -1 -> "X"
    0 -> "O"
    else -> "$value 品"
}
