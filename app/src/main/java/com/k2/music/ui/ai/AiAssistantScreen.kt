package com.k2.music.ui.ai

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.gateway.AiAcceptKind
import com.k2.music.ui.gateway.AiResultUi
import com.k2.music.ui.gateway.AiTaskUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AiAssistantRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenChord: (String) -> Unit,
    onOpenProgression: (String) -> Unit,
    onOpenPractice: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(AiAssistantViewModel::class) { handle ->
            AiAssistantViewModel(services.aiGateway, handle)
        }
    }
    val viewModel: AiAssistantViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshConfiguration()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiAssistantEffect.OpenProgression -> onOpenProgression(effect.seed)
                is AiAssistantEffect.OpenPractice -> {
                    snackbarHostState.showSnackbar("已接受 ${effect.title}，打开练习设置后仍可修改")
                    onOpenPractice()
                }
                is AiAssistantEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    AiAssistantScreen(
        state = state,
        onBack = onBack,
        onSettings = onSettings,
        onTask = viewModel::setTask,
        onInput = viewModel::setInput,
        onSend = viewModel::send,
        onCancel = viewModel::cancel,
        onAccept = viewModel::accept,
        onOpenChord = onOpenChord,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    state: AiAssistantUiState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onTask: (AiTaskUi) -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onOpenChord: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("ai_assistant_screen"),
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TextButton(onClick = onSettings) { Text("设置") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("offline_boundary") {
                InlineMessage("AI 是默认关闭的可选增强层。所有和弦、按法、进行与练习结果仍由本地核心验证。")
            }
            if (!state.configured) {
                item("not_configured") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("AI 尚未配置", style = MaterialTheme.typography.titleLarge)
                            Text("离线功能不受影响。只有完成 HTTPS 服务、模型和密钥配置后，主动发送才会联网。")
                            Button(onClick = onSettings) { Text("打开 AI 设置") }
                        }
                    }
                }
            }
            item("tasks") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiTaskUi.entries, key = { it.name }) { task ->
                        FilterChip(
                            selected = state.task == task,
                            onClick = { onTask(task) },
                            label = { Text(task.label) },
                        )
                    }
                }
            }
            item("input") {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth().testTag("ai_input"),
                    label = { Text(state.task.label) },
                    supportingText = { Text(state.task.helper) },
                    minLines = 3,
                    enabled = !state.loading,
                )
            }
            if (state.task == AiTaskUi.PRACTICE_PLAN) {
                item("privacy_confirm") {
                    InlineMessage("上方内容就是本次将发送的摘要；请确认其中不包含你不希望发送的信息。")
                }
            }
            item("send") {
                if (state.loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null)
                            Text("取消请求")
                        }
                        CircularProgressIndicator(Modifier.align(Alignment.CenterVertically))
                    }
                } else {
                    Button(
                        onClick = onSend,
                        enabled = state.configured,
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("ai_send"),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("发送并本地校验")
                    }
                }
            }
            state.error?.let { failure ->
                item("error") {
                    InlineMessage("${failure.type}：${failure.message}", isError = true)
                }
            }
            state.result?.let { result ->
                item("result") {
                    AiResultCard(result, onAccept, onOpenChord)
                }
            }
        }
    }
}

@Composable
private fun AiResultCard(
    result: AiResultUi,
    onAccept: () -> Unit,
    onOpenChord: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("AI 建议", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                }
                Text(result.title, style = MaterialTheme.typography.headlineSmall)
                Text(result.aiExplanation, style = MaterialTheme.typography.bodyLarge)
                result.items.forEach { item ->
                    Card(
                        onClick = { if (result.task == AiTaskUi.RECOMMEND_CHORDS) onOpenChord(item.title) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.detail, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (result.task == AiTaskUi.RECOMMEND_CHORDS) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "打开 ${item.title} 详情")
                            }
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text("本地验证", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                }
                Text(result.localValidation)
                if (result.rejected.isNotEmpty()) {
                    Text("已拒绝：${result.rejected.joinToString("、")}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (result.acceptKind != AiAcceptKind.NONE) {
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(if (result.acceptKind == AiAcceptKind.PROGRESSION) "确认并打开进行草稿" else "确认并保存练习草稿")
            }
        }
    }
}
