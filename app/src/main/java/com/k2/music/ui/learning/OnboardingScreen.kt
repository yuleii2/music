package com.k2.music.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.PracticePreferences
import com.k2.music.PracticePreferencesStore
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.preferences.capabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingUiState(
    val step: Int = 0,
    val skillLevel: SkillLevel = SkillLevel.BEGINNER,
    val goals: Set<LearningGoal> = setOf(LearningGoal.BASIC_CHORDS, LearningGoal.CHORD_TRANSITIONS),
    val dailyMinutes: Int = 5,
    val experienceMode: ExperienceMode = ExperienceMode.BEGINNER,
    val saving: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val learningStore: LearningProfileStore,
    private val appPreferences: AppPreferences,
    private val practicePreferencesStore: PracticePreferencesStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val existing = learningStore.profile.value
    private val _state = MutableStateFlow(
        OnboardingUiState(
            step = (savedStateHandle[KEY_STEP] ?: 0).coerceIn(0, 3),
            skillLevel = enumValue(savedStateHandle[KEY_SKILL], existing.skillLevel),
            goals = savedStateHandle.get<Array<String>>(KEY_GOALS)
                ?.mapNotNull { enumValueOrNull<LearningGoal>(it) }
                ?.toSet()
                ?: existing.goals,
            dailyMinutes = (savedStateHandle[KEY_DAILY] ?: existing.dailyTargetMinutes).coerceIn(1, 180),
            experienceMode = enumValue(savedStateHandle[KEY_EXPERIENCE], existing.preferredExperienceMode),
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() {
        if (_state.value.step == 2 && _state.value.goals.isEmpty()) {
            _state.value = _state.value.copy(error = "请选择至少一个主要目标。")
            return
        }
        val next = (_state.value.step + 1).coerceAtMost(3)
        savedStateHandle[KEY_STEP] = next
        _state.value = _state.value.copy(step = next, error = null)
    }

    fun back() {
        val previous = (_state.value.step - 1).coerceAtLeast(0)
        savedStateHandle[KEY_STEP] = previous
        _state.value = _state.value.copy(step = previous, error = null)
    }

    fun setSkill(value: SkillLevel) {
        savedStateHandle[KEY_SKILL] = value.name
        _state.value = _state.value.copy(skillLevel = value)
    }

    fun toggleGoal(value: LearningGoal) {
        val current = _state.value.goals.toMutableSet()
        if (!current.add(value)) current.remove(value)
        if (current.size > 2) {
            _state.value = _state.value.copy(error = "最多选择两个主要目标。")
            return
        }
        savedStateHandle[KEY_GOALS] = current.map { it.name }.toTypedArray()
        _state.value = _state.value.copy(goals = current, error = null)
    }

    fun setDailyMinutes(value: Int) {
        val safe = value.coerceIn(1, 180)
        savedStateHandle[KEY_DAILY] = safe
        _state.value = _state.value.copy(dailyMinutes = safe)
    }

    fun setExperience(value: ExperienceMode) {
        savedStateHandle[KEY_EXPERIENCE] = value.name
        _state.value = _state.value.copy(experienceMode = value)
    }

    fun finish() = persist(skip = false)
    fun skip() = persist(skip = true)

    private fun persist(skip: Boolean) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val profile = if (skip) {
                    withContext(Dispatchers.IO) { learningStore.skip() }
                } else {
                    withContext(Dispatchers.IO) {
                        learningStore.save(
                            existing.copy(
                                onboardingCompleted = true,
                                skillLevel = _state.value.skillLevel,
                                goals = _state.value.goals,
                                dailyTargetMinutes = _state.value.dailyMinutes,
                                preferredExperienceMode = _state.value.experienceMode,
                            ),
                        )
                    }
                }
                appPreferences.setExperienceMode(profile.preferredExperienceMode)
                withContext(Dispatchers.IO) { applyPracticeDefaults(profile) }
            }.onSuccess {
                _state.value = _state.value.copy(saving = false)
            }.onFailure {
                _state.value = _state.value.copy(saving = false, error = it.message ?: "学习设置保存失败。")
            }
        }
    }

    private fun applyPracticeDefaults(profile: LearningProfile) {
        val previous = practicePreferencesStore.load()
        val capabilities = profile.preferredExperienceMode.capabilities()
        val proficiency = when (profile.skillLevel) {
            SkillLevel.BEGINNER -> PracticePreferences.Proficiency.BEGINNER
            SkillLevel.BASIC -> PracticePreferences.Proficiency.INTERMEDIATE
            SkillLevel.INTERMEDIATE -> PracticePreferences.Proficiency.ADVANCED
        }
        practicePreferencesStore.save(
            PracticePreferences(
                proficiency,
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

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValueOrNull<T>(raw) ?: fallback

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        enumValues<T>().firstOrNull { it.name == raw }

    private companion object {
        const val KEY_STEP = "onboarding_step"
        const val KEY_SKILL = "onboarding_skill"
        const val KEY_GOALS = "onboarding_goals"
        const val KEY_DAILY = "onboarding_daily"
        const val KEY_EXPERIENCE = "onboarding_experience"
    }
}

@Composable
fun OnboardingRoute(
    learningStore: LearningProfileStore,
    appPreferences: AppPreferences,
    practicePreferencesStore: PracticePreferencesStore,
    instanceKey: Long,
) {
    val factory = remember(learningStore, appPreferences, practicePreferencesStore) {
        MusicViewModelFactory(OnboardingViewModel::class) { handle ->
            OnboardingViewModel(learningStore, appPreferences, practicePreferencesStore, handle)
        }
    }
    val viewModel: OnboardingViewModel = viewModel(key = "onboarding-$instanceKey", factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onSkill = viewModel::setSkill,
        onGoal = viewModel::toggleGoal,
        onDailyMinutes = viewModel::setDailyMinutes,
        onExperience = viewModel::setExperience,
        onFinish = viewModel::finish,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onSkill: (SkillLevel) -> Unit,
    onGoal: (LearningGoal) -> Unit,
    onDailyMinutes: (Int) -> Unit,
    onExperience: (ExperienceMode) -> Unit,
    onFinish: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("onboarding_screen"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("progress") {
            LinearProgressIndicator(
                progress = { (state.step + 1) / 4f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("content") {
            when (state.step) {
                0 -> WelcomeStep()
                1 -> SkillStep(state, onSkill)
                2 -> GoalStep(state, onGoal)
                else -> TimeAndModeStep(state, onDailyMinutes, onExperience)
            }
        }
        state.error?.let { item("error") { InlineMessage(it, isError = true) } }
        item("actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (state.step == 3) onFinish else onNext,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(if (state.step == 0) "开始设置" else if (state.step == 3) "完成设置" else "继续") }
                if (state.step > 0) TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("上一步") }
                TextButton(
                    onClick = onSkip,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_skip"),
                ) {
                    Text("跳过，使用默认设置")
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("吉他和弦工作室", style = MaterialTheme.typography.displaySmall)
        Text("查询和弦、练习切换、编排进行并记录进步。", style = MaterialTheme.typography.titleLarge)
        Text("用不到一分钟设置当前水平和每天可投入的时间，首页会据此安排今天的内容。")
    }
}

@Composable
private fun SkillStep(state: OnboardingUiState, onSkill: (SkillLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("你目前处于什么阶段？", style = MaterialTheme.typography.headlineMedium)
        SkillLevel.entries.forEach { level ->
            Card(onClick = { onSkill(level) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(level.label, style = MaterialTheme.typography.titleMedium)
                    Text(level.description)
                    Text(if (state.skillLevel == level) "已选择" else "点按选择", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun GoalStep(state: OnboardingUiState, onGoal: (LearningGoal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("选择一到两个主要目标", style = MaterialTheme.typography.headlineMedium)
        AdaptiveControlGroup {
            LearningGoal.entries.forEach { goal ->
                FilterChip(
                    selected = goal in state.goals,
                    onClick = { onGoal(goal) },
                    label = { Text(goal.label) },
                )
            }
        }
        Text("当前已选择 ${state.goals.size} / 2 项", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeAndModeStep(
    state: OnboardingUiState,
    onDailyMinutes: (Int) -> Unit,
    onExperience: (ExperienceMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("每天愿意练多久？", style = MaterialTheme.typography.headlineMedium)
        AdaptiveControlGroup {
            listOf(3, 5, 10, 15).forEach { minutes ->
                FilterChip(
                    selected = state.dailyMinutes == minutes,
                    onClick = { onDailyMinutes(minutes) },
                    label = { Text("$minutes 分钟") },
                )
            }
        }
        OutlinedTextField(
            value = state.dailyMinutes.toString(),
            onValueChange = { it.toIntOrNull()?.let(onDailyMinutes) },
            label = { Text("自定义分钟数") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("界面信息密度", style = MaterialTheme.typography.titleMedium)
        AdaptiveControlGroup {
            FilterChip(
                selected = state.experienceMode == ExperienceMode.BEGINNER,
                onClick = { onExperience(ExperienceMode.BEGINNER) },
                label = { Text("新手：先看推荐按法和下一步") },
            )
            FilterChip(
                selected = state.experienceMode == ExperienceMode.PROFESSIONAL,
                onClick = { onExperience(ExperienceMode.PROFESSIONAL) },
                label = { Text("专业：默认展开理论和高级参数") },
            )
        }
    }
}
