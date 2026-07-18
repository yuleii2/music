package com.k2.music.ui.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.k2.music.ui.components.StudioTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.ExportFormatUi
import com.k2.music.ui.gateway.ExportGateway
import com.k2.music.ui.gateway.ExportProgressUi
import com.k2.music.ui.gateway.ExportRequestUi
import com.k2.music.ui.gateway.ExportScopeUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ExportUiState(
    val request: ExportRequestUi,
    val itemCount: Int = 0,
    val folderUri: String = "",
    val prefix: String = "chord",
    val format: ExportFormatUi = ExportFormatUi.JPG,
    val progress: ExportProgressUi? = null,
    val error: String? = null,
)

sealed interface ExportEffect {
    data class Message(val text: String) : ExportEffect
}

class ExportViewModel(
    private val gateway: ExportGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val request = ExportRequestUi(
        scope = enumValue(savedStateHandle["scope"], ExportScopeUi.FAVORITES),
        symbols = savedStateHandle.get<String>("symbols").orEmpty().split('\n').filter { it.isNotBlank() },
        currentVoicingIndex = savedStateHandle["index"] ?: 0,
    )
    private val _state = MutableStateFlow(
        ExportUiState(
            request = request,
            folderUri = savedStateHandle[KEY_FOLDER] ?: "",
            prefix = savedStateHandle[KEY_PREFIX] ?: "chord",
            format = enumValue(savedStateHandle[KEY_FORMAT], ExportFormatUi.JPG),
        ),
    )
    private val effectsChannel = Channel<ExportEffect>(Channel.BUFFERED)
    private var exportJob: Job? = null
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching { gateway.count(request) }
                .onSuccess { _state.value = _state.value.copy(itemCount = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "无法准备导出项。") }
        }
    }

    fun setFolder(uri: Uri) {
        savedStateHandle[KEY_FOLDER] = uri.toString()
        _state.value = _state.value.copy(folderUri = uri.toString(), error = null)
    }

    fun setPrefix(value: String) {
        savedStateHandle[KEY_PREFIX] = value
        _state.value = _state.value.copy(prefix = value, error = null)
    }

    fun setFormat(value: ExportFormatUi) {
        savedStateHandle[KEY_FORMAT] = value.name
        _state.value = _state.value.copy(format = value)
    }

    fun start() {
        val snapshot = _state.value
        if (snapshot.folderUri.isBlank()) {
            _state.value = snapshot.copy(error = "请先选择导出文件夹。")
            return
        }
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.value = snapshot.copy(
                progress = ExportProgressUi(snapshot.itemCount, 0, 0, 0),
                error = null,
            )
            runCatching {
                gateway.export(
                    snapshot.request,
                    snapshot.folderUri,
                    snapshot.prefix,
                    snapshot.format,
                ) { progress -> _state.value = _state.value.copy(progress = progress) }
            }.onSuccess { result ->
                _state.value = _state.value.copy(progress = result)
                effectsChannel.send(
                    ExportEffect.Message("导出完成：成功 ${result.succeeded}，失败 ${result.failed}"),
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    val current = _state.value.progress
                    _state.value = _state.value.copy(
                        progress = current?.copy(running = false, cancelled = true),
                    )
                } else {
                    _state.value = _state.value.copy(
                        progress = _state.value.progress?.copy(running = false),
                        error = error.message ?: "导出失败。",
                    )
                }
            }
        }
    }

    fun cancel() {
        exportJob?.cancel()
        exportJob = null
        _state.value = _state.value.copy(
            progress = _state.value.progress?.copy(running = false, cancelled = true),
        )
        viewModelScope.launch { effectsChannel.send(ExportEffect.Message("导出已取消")) }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_FOLDER = "export_folder"
        const val KEY_PREFIX = "export_prefix"
        const val KEY_FORMAT = "export_format"
    }
}

@Composable
fun ExportRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(ExportViewModel::class) { handle -> ExportViewModel(services.exportGateway, handle) }
    }
    val viewModel: ExportViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    ExportScreen(
        state,
        onBack,
        viewModel::setFolder,
        viewModel::setPrefix,
        viewModel::setFormat,
        viewModel::start,
        viewModel::cancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onBack: () -> Unit,
    onFolder: (Uri) -> Unit,
    onPrefix: (String) -> Unit,
    onFormat: (ExportFormatUi) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onFolder(it)
        }
    }
    val running = state.progress?.running == true
    BackHandler(enabled = running) { confirmLeave = true }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("export_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text("导出指法") },
                navigationIcon = {
                    IconButton(onClick = { if (running) confirmLeave = true else onBack() }) {
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
            item("summary") {
                InlineMessage("当前范围包含 ${state.itemCount} 个可导出指法。图片与 SVG 都由后台导出器生成，不使用屏幕截图。")
            }
            item("folder") {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导出文件夹", style = MaterialTheme.typography.titleMedium)
                        Text(state.folderUri.ifBlank { "尚未选择" }, style = MaterialTheme.typography.bodySmall)
                        StudioButton(onClick = { launcher.launch(null) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Text("选择文件夹")
                        }
                    }
                }
            }
            item("prefix") {
                OutlinedTextField(
                    state.prefix,
                    onPrefix,
                    Modifier.fillMaxWidth(),
                    label = { Text("文件名前缀") },
                    supportingText = { Text("系统会追加和弦名、序号和按法名，避免覆盖。") },
                    singleLine = true,
                )
            }
            item("format") {
                AdaptiveControlGroup {
                    ExportFormatUi.entries.forEach { format ->
                        FilterChip(
                            selected = state.format == format,
                            onClick = { onFormat(format) },
                            label = { Text(format.label) },
                        )
                    }
                }
            }
            state.error?.let { item("error") { InlineMessage(it, isError = true) } }
            state.progress?.let { progress ->
                item("progress") {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (progress.running) "正在导出 ${progress.completed}/${progress.total}" else if (progress.cancelled) "导出已取消" else "导出完成",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            LinearProgressIndicator(
                                progress = { if (progress.total == 0) 0f else progress.completed / progress.total.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("成功 ${progress.succeeded} · 失败 ${progress.failed}")
                            if (progress.firstFileName.isNotBlank()) Text("首个文件：${progress.firstFileName}")
                        }
                    }
                }
            }
            item("action") {
                if (running) {
                    StudioButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("取消导出")
                    }
                } else {
                    StudioButton(
                        onClick = onStart,
                        enabled = state.itemCount > 0,
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_export"),
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开始导出")
                    }
                }
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("取消导出并离开？") },
            text = { Text("已完成的文件会保留，尚未开始的项目将取消。") },
            confirmButton = {
                StudioButton(onClick = {
                    confirmLeave = false
                    onCancel()
                    onBack()
                }) { Text("取消并离开") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("继续导出") } },
        )
    }
}
