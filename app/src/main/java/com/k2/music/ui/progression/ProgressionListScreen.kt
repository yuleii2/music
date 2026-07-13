package com.k2.music.ui.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.EmptyState
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.model.ProgressionPresetUi
import com.k2.music.ui.model.ProgressionSummaryUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProgressionListRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(ProgressionListViewModel::class) { handle ->
            ProgressionListViewModel(services.progressionGateway, handle)
        }
    }
    val viewModel: ProgressionListViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ProgressionListEffect.OpenEditor -> onOpenEditor(effect.id)
                is ProgressionListEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is ProgressionListEffect.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        "已删除 ${effect.name}",
                        actionLabel = "撤销",
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            }
        }
    }
    ProgressionListScreen(
        state = state,
        onBack = onBack,
        onOpen = onOpenEditor,
        onNew = { viewModel.create() },
        onPresetKey = viewModel::setPresetKey,
        onPreset = { viewModel.createFromPreset(it.id) },
        onDuplicate = viewModel::duplicate,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onRetry = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionListScreen(
    state: ProgressionListUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onPresetKey: (String) -> Unit,
    onPreset: (ProgressionPresetUi) -> Unit,
    onDuplicate: (ProgressionSummaryUi) -> Unit,
    onRename: (ProgressionSummaryUi, String) -> Unit,
    onDelete: (ProgressionSummaryUi) -> Unit,
    onRetry: () -> Unit,
) {
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProgressionSummaryUi?>(null) }
    var deleteTarget by remember { mutableStateOf<ProgressionSummaryUi?>(null) }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("progression_list_screen"),
        topBar = {
            TopAppBar(
                title = { Text("和弦进行") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, modifier = Modifier.testTag("new_progression")) {
                Icon(Icons.Rounded.Add, contentDescription = "新建和弦进行")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("intro") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("编排、推荐按法并跟随绝对时间轴播放。", style = MaterialTheme.typography.bodyLarge)
                    AdaptiveControlGroup {
                        FilterChip(
                            selected = !showPresets,
                            onClick = { showPresets = false },
                            label = { Text("本地进行") },
                        )
                        FilterChip(
                            selected = showPresets,
                            onClick = { showPresets = true },
                            label = { Text("预设") },
                        )
                    }
                }
            }
            if (state.loading) {
                item("loading") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.error?.let { message ->
                item("error") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InlineMessage(message, isError = true)
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
            }
            if (!state.loading && state.error == null && !showPresets) {
                if (state.saved.isEmpty()) {
                    item("empty") {
                        EmptyState(
                            title = "尚未保存进行",
                            message = "新建一个进行，或从本地预设开始。",
                            action = { Button(onClick = onNew) { Text("新建进行") } },
                        )
                    }
                } else {
                    items(state.saved, key = { it.id }, contentType = { "saved_progression" }) { item ->
                        SavedProgressionCard(
                            item,
                            onOpen = { onOpen(item.id) },
                            onDuplicate = { onDuplicate(item) },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                }
            }
            if (!state.loading && state.error == null && showPresets) {
                item("preset_key") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("预设调性", style = MaterialTheme.typography.titleMedium)
                        AdaptiveControlGroup {
                            listOf("C", "G", "D", "A", "F", "Bb").forEach { key ->
                                FilterChip(
                                    selected = state.presetKey == key,
                                    onClick = { onPresetKey(key) },
                                    label = { Text(key) },
                                )
                            }
                        }
                    }
                }
                items(state.presets, key = { it.id }, contentType = { "preset" }) { preset ->
                    PresetCard(preset, onUse = { onPreset(preset) })
                }
            }
        }
    }

    renameTarget?.let { item ->
        RenameProgressionDialog(
            item = item,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRename(item, name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除和弦进行？") },
            text = { Text("将删除“${item.name}”。删除后仍可立即撤销。") },
            confirmButton = {
                Button(onClick = {
                    onDelete(item)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SavedProgressionCard(
    item: ProgressionSummaryUi,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("progression_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${item.stepCount} 步", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "${item.keySignature.ifBlank { "未设调性" }} · ${item.bpm} BPM · ${item.timeSignature}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(item.symbols.ifBlank { "空进行" }, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("复制")
                }
                TextButton(onClick = onRename, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Text("重命名")
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: ProgressionPresetUi, onUse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preset.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${preset.keySignature} 大调 · 每个和弦 ${formatBeats(preset.beatsPerChord)} 拍",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(preset.symbols.joinToString("  →  "), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onUse, modifier = Modifier.align(Alignment.End)) { Text("使用预设") }
        }
    }
}

@Composable
private fun RenameProgressionDialog(
    item: ProgressionSummaryUi,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名进行") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("进行名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatBeats(value: Double): String =
    if (value == kotlin.math.floor(value)) value.toInt().toString() else value.toString()
