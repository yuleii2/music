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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AiSettingsRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(AiSettingsViewModel::class) { AiSettingsViewModel(services.aiGateway) }
    }
    val viewModel: AiSettingsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiSettingsEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                AiSettingsEffect.ClearKeyField -> apiKey = ""
            }
        }
    }
    AiSettingsScreen(
        state = state,
        apiKey = apiKey,
        onApiKey = { apiKey = it },
        onBack = onBack,
        onEnabled = viewModel::setEnabled,
        onService = viewModel::setService,
        onBaseUrl = viewModel::setBaseUrl,
        onModel = viewModel::setModel,
        onTemperature = viewModel::setTemperature,
        onTimeout = viewModel::setTimeout,
        onSave = { viewModel.save(apiKey) },
        onTest = viewModel::testConnection,
        onCancelTest = viewModel::cancelTest,
        onClear = viewModel::clearConfiguration,
        onClearCache = viewModel::clearCache,
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    apiKey: String,
    onApiKey: (String) -> Unit,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onService: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onTemperature: (Double) -> Unit,
    onTimeout: (Int) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onCancelTest: () -> Unit,
    onClear: () -> Unit,
    onClearCache: () -> Unit,
) {
    val settings = state.settings
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("ai_settings_screen"),
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("security") { InlineMessage("API Key 仅以 Android Keystore + AES-GCM 密文保存，界面不会回显现有密钥。") }
            item("enabled") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用 AI", style = MaterialTheme.typography.titleMedium)
                        Text(if (settings.enabled) "只在你主动发送时联网" else "当前为零网络请求", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = settings.enabled, onCheckedChange = onEnabled)
                }
            }
            item("service") {
                OutlinedTextField(settings.serviceName, onService, Modifier.fillMaxWidth(), label = { Text("服务名称") }, singleLine = true)
            }
            item("url") {
                OutlinedTextField(
                    settings.baseUrl,
                    onBaseUrl,
                    Modifier.fillMaxWidth(),
                    label = { Text("HTTPS Base URL") },
                    supportingText = { Text("必须使用 https://，可指向 OpenAI Compatible 服务") },
                    singleLine = true,
                )
            }
            item("key") {
                OutlinedTextField(
                    apiKey,
                    onApiKey,
                    Modifier.fillMaxWidth().testTag("ai_api_key"),
                    label = { Text(if (settings.hasApiKey) "API Key（已保存，留空则保留）" else "API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
            item("model") {
                OutlinedTextField(settings.model, onModel, Modifier.fillMaxWidth(), label = { Text("模型") }, singleLine = true)
            }
            item("temperature") {
                Column {
                    Text("温度：${"%.1f".format(settings.temperature)}")
                    Slider(
                        value = settings.temperature.toFloat(),
                        onValueChange = { onTemperature(it.toDouble()) },
                        valueRange = 0f..2f,
                        steps = 19,
                    )
                }
            }
            item("timeout") {
                Column {
                    Text("超时：${settings.timeoutSeconds} 秒")
                    Slider(
                        value = settings.timeoutSeconds.toFloat(),
                        onValueChange = { onTimeout(it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 114,
                    )
                }
            }
            state.error?.let { item("error") { InlineMessage(it, isError = true) } }
            item("save") {
                Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.saving) "保存中…" else "保存设置")
                }
            }
            item("test") {
                if (state.testing) {
                    Button(onClick = onCancelTest, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("取消连接测试")
                    }
                } else {
                    Button(onClick = onTest, enabled = settings.enabled, modifier = Modifier.fillMaxWidth()) { Text("测试连接") }
                }
            }
            item("clear") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClearCache, modifier = Modifier.weight(1f)) { Text("清除缓存") }
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        Text("清除配置")
                    }
                }
            }
        }
    }
}
