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
import com.k2.music.ui.components.StudioButton
import androidx.compose.material3.CircularProgressIndicator
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
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.components.StudioGroup
import com.k2.music.ui.components.StudioPageHeader
import com.k2.music.ui.components.StudioSegmentedControl
import com.k2.music.ui.gateway.AiGateway
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeSummaryUi
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.preferences.MotionLevel
import com.k2.music.ui.preferences.ThemeMode
import com.k2.music.ui.learning.LearningProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import com.k2.music.ui.preferences.capabilities
import com.k2.music.PracticePreferences

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
    onPracticeProgress: () -> Unit,
    onDataBackup: () -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(ProfileViewModel::class) {
            ProfileViewModel(services.practiceGateway, services.aiGateway)
        }
    }
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by appPreferences.settings.collectAsStateWithLifecycle()
    val learningProfile by services.learningProfileStore.profile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    ProfileScreen(
        state,
        settings,
        learningProfile,
        onTheme = appPreferences::setThemeMode,
        onMotion = appPreferences::setMotionLevel,
        onExperience = { mode ->
            appPreferences.setExperienceMode(mode)
            services.learningProfileStore.save(learningProfile.copy(preferredExperienceMode = mode))
            scope.launch(Dispatchers.IO) {
                val previous = services.practicePreferencesStore.load()
                val capabilities = mode.capabilities()
                services.practicePreferencesStore.save(
                    PracticePreferences(
                        previous.proficiency,
                        capabilities.defaultAllowBarre,
                        capabilities.defaultMaxFret,
                        capabilities.defaultPracticeBpm,
                        previous.defaultTimeSignature,
                        previous.defaultPlaybackMode,
                        previous.accentFirstBeat,
                        previous.familiarVoicingIds,
                    ),
                )
            }
        },
        onDynamicColor = appPreferences::setDynamicColor,
        onAiAssistant = onAiAssistant,
        onAiSettings = onAiSettings,
        onExportFavorites = onExportFavorites,
        onRerunOnboarding = services.learningProfileStore::rerun,
        onPracticeProgress = onPracticeProgress,
        onDataBackup = onDataBackup,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    settings: AppSettings,
    learningProfile: LearningProfile,
    onTheme: (ThemeMode) -> Unit,
    onMotion: (MotionLevel) -> Unit,
    onExperience: (ExperienceMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAiAssistant: () -> Unit,
    onAiSettings: () -> Unit,
    onExportFavorites: () -> Unit,
    onRerunOnboarding: () -> Unit,
    onPracticeProgress: () -> Unit,
    onDataBackup: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            StudioPageHeader("设置", "本地偏好、学习资料与数据管理。")
        }
        if (state.loading) item("loading") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        item("practice") {
            ProfileSection(Icons.Rounded.School, "练习概览") {
                AdaptiveStatGrid(
                    listOf(
                        AdaptiveStat("今日", "${state.summary.todaySeconds / 60} 分"),
                        AdaptiveStat("近 7 天", "${state.summary.sevenDaySessions} 次"),
                        AdaptiveStat("总尝试", "${state.summary.sevenDayAttempts} 次"),
                        AdaptiveStat("成功率", state.summary.sevenDaySuccessRate?.let { "%.0f%%".format(it * 100) } ?: "数据不足"),
                        AdaptiveStat("最佳连续", state.summary.bestStreak.toString()),
                    ),
                )
                StudioButton(onClick = onPracticeProgress) { Text("查看练习进步") }
            }
        }
        item("learning_profile") {
            ProfileSection(Icons.Rounded.School, "学习设置") {
                Text("当前水平：${learningProfile.skillLevel.label}")
                Text("学习目标：${learningProfile.goals.joinToString("、") { it.label }}")
                Text("每日练习目标：${learningProfile.dailyTargetMinutes} 分钟")
                TextButton(onClick = onRerunOnboarding) { Text("重新运行首次引导") }
                Text("重新设置不会删除已有练习数据。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item("appearance") {
            ProfileSection(Icons.Rounded.ColorLens, "外观与动画") {
                Text("主题", style = MaterialTheme.typography.labelLarge)
                StudioSegmentedControl(
                    options = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色",
                    ),
                    selected = settings.themeMode,
                    onSelected = onTheme,
                )
                Text("动画", style = MaterialTheme.typography.labelLarge)
                StudioSegmentedControl(
                    options = listOf(
                        MotionLevel.FULL to "完整",
                        MotionLevel.REDUCED to "简化",
                        MotionLevel.OFF to "关闭",
                    ),
                    selected = settings.motionLevel,
                    onSelected = onMotion,
                )
                Text("体验模式", style = MaterialTheme.typography.labelLarge)
                StudioSegmentedControl(
                    options = ExperienceMode.entries.map { mode ->
                        mode to if (mode == ExperienceMode.BEGINNER) "新手" else "专业"
                    },
                    selected = settings.experienceMode,
                    onSelected = onExperience,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("系统强调色")
                        Text("仅替换按钮与选中状态，界面材质保持不变", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColor)
                }
            }
        }
        item("ai") {
            ProfileSection(Icons.Rounded.AutoAwesome, "AI 状态") {
                Text(if (state.aiConfigured) "已配置；仍只在主动发送时联网" else "未配置；当前零网络请求")
                AdaptiveControlGroup {
                    StudioButton(onClick = onAiAssistant, enabled = state.aiConfigured) { Text("AI 助手") }
                    TextButton(onClick = onAiSettings) { Text("AI 设置") }
                }
            }
        }
        item("data") {
            ProfileSection(Icons.Rounded.DataObject, "数据与导出") {
                Text("本地收藏、历史、自定义指法、进行和练习记录不会上传。")
                StudioButton(onClick = onDataBackup) { Text("数据与备份") }
                StudioButton(onClick = onExportFavorites) { Text("导出收藏指法") }
            }
        }
        item("about") {
            ProfileSection(Icons.Rounded.Info, "关于软件") {
                Text("吉他和弦工作室 Android V1.6")
                Text("离线乐理、练习、音频、存储与导出核心。", style = MaterialTheme.typography.bodyMedium)
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
    StudioGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
            }
            content()
        }
    }
}
