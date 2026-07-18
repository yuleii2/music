package com.k2.music.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.k2.music.ui.components.StudioTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.BackupPreview
import com.k2.music.FullBackupManager
import com.k2.music.RestoreMode
import com.k2.music.RestoreReport
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.components.InlineMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

data class DataBackupUiState(
    val running: Boolean = false,
    val selectedUri: Uri? = null,
    val preview: BackupPreview? = null,
    val restoreMode: RestoreMode = RestoreMode.MERGE,
    val restoreSettings: Boolean = false,
    val report: RestoreReport? = null,
    val message: String? = null,
    val error: String? = null,
    val confirmRestore: Boolean = false,
    val confirmLeave: Boolean = false,
)

class DataBackupViewModel(private val manager: FullBackupManager) : ViewModel() {
    private val _state = MutableStateFlow(DataBackupUiState())
    val state = _state.asStateFlow()
    private var activeJob: Job? = null

    fun export(uri: Uri, resolver: ContentResolver) = runTask {
        resolver.openOutputStream(uri, "w")?.use { manager.writeBackup(it) }
            ?: error("无法打开备份保存位置。")
        _state.value = _state.value.copy(message = "完整备份已保存。API Key 未包含在备份中。")
    }

    fun selectRestore(uri: Uri, resolver: ContentResolver) = runTask {
        val preview = resolver.openInputStream(uri)?.use(manager::preview)
            ?: error("无法读取所选备份。")
        _state.value = _state.value.copy(selectedUri = uri, preview = preview, report = null)
    }

    fun setMode(value: RestoreMode) { _state.value = _state.value.copy(restoreMode = value) }
    fun setRestoreSettings(value: Boolean) { _state.value = _state.value.copy(restoreSettings = value) }
    fun requestRestore() { _state.value = _state.value.copy(confirmRestore = true) }
    fun dismissRestore() { _state.value = _state.value.copy(confirmRestore = false) }
    fun requestLeave() { _state.value = _state.value.copy(confirmLeave = true) }
    fun dismissLeave() { _state.value = _state.value.copy(confirmLeave = false) }

    fun restore(resolver: ContentResolver) {
        val uri = _state.value.selectedUri ?: return
        val mode = _state.value.restoreMode
        val settings = _state.value.restoreSettings
        _state.value = _state.value.copy(confirmRestore = false)
        runTask {
            val active = coroutineContext[Job]
            val report = resolver.openInputStream(uri)?.use {
                manager.restore(it, mode, settings) { active?.isActive == false }
            }
                ?: error("无法重新打开备份文件。")
            _state.value = _state.value.copy(report = report, message = "恢复完成。")
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = _state.value.copy(running = false, confirmLeave = false, message = "操作已取消。")
    }

    private fun runTask(block: suspend () -> Unit) {
        if (activeJob?.isActive == true) return
        _state.value = _state.value.copy(running = true, error = null, message = null)
        activeJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(error = error.message ?: "数据操作失败。")
            } finally {
                _state.value = _state.value.copy(running = false)
            }
        }
    }
}

@Composable
fun DataBackupRoute(services: CoreServices, onBack: () -> Unit) {
    val context = LocalContext.current
    val factory = remember(services) {
        MusicViewModelFactory(DataBackupViewModel::class) { DataBackupViewModel(services.fullBackupManager) }
    }
    val viewModel: DataBackupViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.export(it, context.contentResolver) } }
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectRestore(it, context.contentResolver) }
    }
    DataBackupScreen(
        state = state,
        onBack = onBack,
        onCreateBackup = {
            val name = "music-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip"
            createBackup.launch(name)
        },
        onChooseBackup = { chooseBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
        onMode = viewModel::setMode,
        onRestoreSettings = viewModel::setRestoreSettings,
        onRequestRestore = viewModel::requestRestore,
        onDismissRestore = viewModel::dismissRestore,
        onConfirmRestore = { viewModel.restore(context.contentResolver) },
        onRequestLeave = viewModel::requestLeave,
        onDismissLeave = viewModel::dismissLeave,
        onCancel = viewModel::cancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupScreen(
    state: DataBackupUiState,
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onChooseBackup: () -> Unit,
    onMode: (RestoreMode) -> Unit,
    onRestoreSettings: (Boolean) -> Unit,
    onRequestRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onRequestLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(enabled = state.running, onBack = onRequestLeave)
    Scaffold(
        modifier = Modifier.testTag("data_backup_screen"),
        topBar = {
            StudioTopAppBar(
                title = { Text("数据与备份") },
                navigationIcon = {
                    IconButton(onClick = if (state.running) onRequestLeave else onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("data_backup_list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("description") {
                InlineMessage("完整备份包含收藏、历史、自定义指法、熟悉按法、进行与草稿、练习会话、切换明细、本地曲谱、曲谱练习与困难标记、学习资料和非敏感设置；不包含 API Key、日志、缓存或设备 URI 权限。")
            }
            item("export") {
                StudioButton(onClick = onCreateBackup, enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("完整备份")
                }
            }
            item("restore-file") {
                StudioButton(onClick = onChooseBackup, enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("从备份恢复")
                }
            }
            if (state.running) item("progress") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("正在后台校验或处理文件…", modifier = Modifier.weight(1f))
                    TextButton(onClick = onRequestLeave) { Text("取消") }
                }
            }
            state.error?.let { item("error") { InlineMessage(it, isError = true) } }
            state.message?.let { item("message") { InlineMessage(it) } }
            state.preview?.let { preview ->
                item("preview-title") { Text("恢复预览", style = MaterialTheme.typography.titleLarge) }
                item("preview-stats") {
                    AdaptiveStatGrid(
                        listOf(
                            AdaptiveStat("收藏", preview.favoriteCount.toString()),
                            AdaptiveStat("自定义指法", preview.customVoicingCount.toString()),
                            AdaptiveStat("和弦进行", preview.progressionCount.toString()),
                            AdaptiveStat("练习记录", preview.practiceSessionCount.toString()),
                            AdaptiveStat("切换明细", preview.transitionAttemptCount.toString()),
                            AdaptiveStat("本地曲谱", preview.songProjectCount.toString()),
                            AdaptiveStat("曲谱练习", preview.songPracticeRunCount.toString()),
                            AdaptiveStat("曲谱困难", preview.songDifficultyCount.toString()),
                            AdaptiveStat("备份版本", "schema ${preview.schemaVersion}"),
                        ),
                    )
                }
                if (preview.incompatible) item("incompatible") { InlineMessage("该备份版本高于当前应用，不能恢复。", isError = true) }
                if (!preview.incompatible) {
                    item("mode") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("恢复方式", style = MaterialTheme.typography.titleMedium)
                            AdaptiveControlGroup {
                                FilterChip(selected = state.restoreMode == RestoreMode.MERGE, onClick = { onMode(RestoreMode.MERGE) }, label = { Text("合并") })
                                FilterChip(selected = state.restoreMode == RestoreMode.OVERWRITE, onClick = { onMode(RestoreMode.OVERWRITE) }, label = { Text("覆盖") })
                            }
                            if (state.restoreMode == RestoreMode.MERGE) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("同时恢复非敏感设置", modifier = Modifier.weight(1f))
                                    Switch(checked = state.restoreSettings, onCheckedChange = onRestoreSettings)
                                }
                            }
                        }
                    }
                    item("restore") {
                        StudioButton(onClick = onRequestRestore, enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("确认恢复")
                        }
                    }
                }
            }
            state.report?.let { report ->
                item("report") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("恢复结果", style = MaterialTheme.typography.titleLarge)
                        AdaptiveStatGrid(
                            listOf(
                                AdaptiveStat("成功", report.successfulItems.toString()),
                                AdaptiveStat("跳过", report.skippedItems.toString()),
                                AdaptiveStat("冲突", report.conflictItems.toString()),
                                AdaptiveStat("失败", report.failedItems.toString()),
                            ),
                        )
                        report.messages.forEach { Text(it) }
                    }
                }
            }
        }
    }
    if (state.confirmRestore) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text(if (state.restoreMode == RestoreMode.MERGE) "合并恢复？" else "覆盖本地数据？") },
            text = { Text(if (state.restoreMode == RestoreMode.MERGE) "相同 ID 会去重，内容冲突会保留恢复副本或本机练习记录。" else "本地非敏感数据将替换为备份内容；API Key 不会被备份覆盖。") },
            confirmButton = { StudioButton(onClick = onConfirmRestore) { Text("继续恢复") } },
            dismissButton = { TextButton(onClick = onDismissRestore) { Text("取消") } },
        )
    }
    if (state.confirmLeave) {
        AlertDialog(
            onDismissRequest = onDismissLeave,
            title = { Text("取消当前数据操作？") },
            text = { Text("已完成写入的恢复步骤会由事务快照回滚。") },
            confirmButton = { StudioButton(onClick = onCancel) { Text("取消操作") } },
            dismissButton = { TextButton(onClick = onDismissLeave) { Text("继续等待") } },
        )
    }
}
