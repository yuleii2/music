package com.k2.music.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.AiGateway
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeSummaryUi
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.preferences.MotionLevel
import com.k2.music.ui.preferences.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val summary: PracticeSummaryUi = PracticeSummaryUi(),
    val aiConfigured: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(
    private val practiceGateway: PracticeGateway,
    private val aiGateway: AiGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            runCatching { practiceGateway.summary() }
                .onSuccess { _state.value = ProfileUiState(false, it, aiGateway.isConfigured()) }
                .onFailure { _state.value = ProfileUiState(false, aiConfigured = aiGateway.isConfigured(), error = it.message) }
        }
    }
}

@Composable
fun ProfileRoute(
    services: CoreServices,
    appPreferences: AppPreferences,
    onAiAssistant: () -> Unit,
    onAiSettings: () -> Unit,
    onExportFavorites: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(ProfileViewModel::class) {
            ProfileViewModel(services.practiceGateway, services.aiGateway)
        }
    }
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by appPreferences.settings.collectAsStateWithLifecycle()
    ProfileScreen(
        state,
        settings,
        onTheme = appPreferences::setThemeMode,
        onMotion = appPreferences::setMotionLevel,
        onExperience = appPreferences::setExperienceMode,
        onDynamicColor = appPreferences::setDynamicColor,
        onAiAssistant = onAiAssistant,
        onAiSettings = onAiSettings,
        onExportFavorites = onExportFavorites,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    settings: AppSettings,
    onTheme: (ThemeMode) -> Unit,
    onMotion: (MotionLevel) -> Unit,
    onExperience: (ExperienceMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAiAssistant: () -> Unit,
    onAiSettings: () -> Unit,
    onExportFavorites: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Text("我的", style = MaterialTheme.typography.headlineLarge)
        }
        if (state.loading) item("loading") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        item("practice") {
            ProfileSection(Icons.Rounded.School, "练习概览") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileValue("今日", "${state.summary.todaySeconds / 60} 分", Modifier.weight(1f))
                    ProfileValue("近 7 天", "${state.summary.sevenDaySessions} 次", Modifier.weight(1f))
                    ProfileValue("最佳连续", "${state.summary.bestStreak}", Modifier.weight(1f))
                }
            }
        }
        item("appearance") {
            ProfileSection(Icons.Rounded.ColorLens, "外观与动画") {
                Text("主题", style = MaterialTheme.typography.labelLarge)
                AdaptiveControlGroup {
                    listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色",
                    ).forEach { (mode, label) ->
                        FilterChip(selected = settings.themeMode == mode, onClick = { onTheme(mode) }, label = { Text(label) })
                    }
                }
                Text("动画", style = MaterialTheme.typography.labelLarge)
                AdaptiveControlGroup {
                    listOf(
                        MotionLevel.FULL to "完整",
                        MotionLevel.REDUCED to "简化",
                        MotionLevel.OFF to "关闭",
                    ).forEach { (level, label) ->
                        FilterChip(selected = settings.motionLevel == level, onClick = { onMotion(level) }, label = { Text(label) })
                    }
                }
                Text("体验模式", style = MaterialTheme.typography.labelLarge)
                AdaptiveControlGroup {
                    ExperienceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.experienceMode == mode,
                            onClick = { onExperience(mode) },
                            label = { Text(if (mode == ExperienceMode.BEGINNER) "新手" else "专业") },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("动态颜色")
                        Text("默认关闭以保持琴房品牌色", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColor)
                }
            }
        }
        item("ai") {
            ProfileSection(Icons.Rounded.AutoAwesome, "AI 状态") {
                Text(if (state.aiConfigured) "已配置；仍只在主动发送时联网" else "未配置；当前零网络请求")
                AdaptiveControlGroup {
                    Button(onClick = onAiAssistant, enabled = state.aiConfigured) { Text("AI 助手") }
                    TextButton(onClick = onAiSettings) { Text("AI 设置") }
                }
            }
        }
        item("data") {
            ProfileSection(Icons.Rounded.DataObject, "数据与导出") {
                Text("本地收藏、历史、自定义指法、进行和练习记录不会上传。")
                Button(onClick = onExportFavorites) { Text("导出收藏指法") }
            }
        }
        item("about") {
            ProfileSection(Icons.Rounded.Info, "关于软件") {
                Text("吉他和弦字典 Android V1.3")
                Text("离线乐理、音频、存储与导出核心；Compose Studio Flow 前端。", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProfileSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
            }
            content()
        }
    }
}

@Composable
private fun ProfileValue(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
