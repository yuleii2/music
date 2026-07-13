package com.k2.music.ui.learning

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.k2.music.ui.preferences.ExperienceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SkillLevel(val label: String, val description: String) {
    BEGINNER("完全新手", "正在学习第一批开放和弦"),
    BASIC("已会基础和弦", "会 C、G、Am、Em 等，希望提高换和弦速度"),
    INTERMEDIATE("有一定基础", "需要更多把位、和弦进行、移调和乐理工具"),
}

enum class LearningGoal(val label: String) {
    BASIC_CHORDS("学习基础和弦"),
    CHORD_TRANSITIONS("提高和弦切换速度"),
    SONG_ACCOMPANIMENT("练习歌曲伴奏"),
    PROGRESSIONS("学习和弦进行"),
    MUSIC_THEORY("理解基础乐理"),
    MUSIC_TOOLS("使用移调、变调夹等工具"),
}

data class LearningProfile(
    val version: Int = SCHEMA_VERSION,
    val onboardingCompleted: Boolean = false,
    val skillLevel: SkillLevel = SkillLevel.BEGINNER,
    val goals: Set<LearningGoal> = setOf(LearningGoal.BASIC_CHORDS, LearningGoal.CHORD_TRANSITIONS),
    val dailyTargetMinutes: Int = 5,
    val preferredExperienceMode: ExperienceMode = ExperienceMode.BEGINNER,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    companion object { const val SCHEMA_VERSION = 1 }
}

/** Versioned, process-persistent learning profile. */
class LearningProfileStore private constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
    )

    internal constructor(preferences: SharedPreferences, testOnly: Unit = Unit) : this(preferences)
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<LearningProfile> = _profile.asStateFlow()

    fun save(value: LearningProfile): LearningProfile = persist(value, preserveTimestamps = false)

    /** Restores validated backup timestamps without treating the import as a profile edit. */
    fun restore(value: LearningProfile): LearningProfile = persist(value, preserveTimestamps = true)

    private fun persist(value: LearningProfile, preserveTimestamps: Boolean): LearningProfile {
        val now = System.currentTimeMillis()
        val normalized = value.copy(
            version = LearningProfile.SCHEMA_VERSION,
            goals = value.goals.take(2).toSet().ifEmpty {
                setOf(LearningGoal.BASIC_CHORDS, LearningGoal.CHORD_TRANSITIONS)
            },
            dailyTargetMinutes = value.dailyTargetMinutes.coerceIn(1, 180),
            createdAt = value.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = if (preserveTimestamps) value.updatedAt.coerceAtLeast(0L) else now,
        )
        preferences.edit(commit = true) {
            putInt(KEY_VERSION, normalized.version)
            putBoolean(KEY_COMPLETED, normalized.onboardingCompleted)
            putString(KEY_SKILL, normalized.skillLevel.name)
            putStringSet(KEY_GOALS, normalized.goals.mapTo(linkedSetOf()) { it.name })
            putInt(KEY_DAILY_MINUTES, normalized.dailyTargetMinutes)
            putString(KEY_EXPERIENCE, normalized.preferredExperienceMode.name)
            putLong(KEY_CREATED_AT, normalized.createdAt)
            putLong(KEY_UPDATED_AT, normalized.updatedAt)
        }
        _profile.value = normalized
        return normalized
    }

    fun skip(): LearningProfile = save(
        LearningProfile(
            onboardingCompleted = true,
            skillLevel = SkillLevel.BEGINNER,
            goals = setOf(LearningGoal.BASIC_CHORDS, LearningGoal.CHORD_TRANSITIONS),
            dailyTargetMinutes = 5,
            preferredExperienceMode = ExperienceMode.BEGINNER,
        ),
    )

    fun rerun() = save(_profile.value.copy(onboardingCompleted = false))

    private fun read(): LearningProfile {
        val storedVersion = preferences.getInt(KEY_VERSION, LearningProfile.SCHEMA_VERSION)
        val goals = preferences.getStringSet(KEY_GOALS, null)
            ?.mapNotNull { raw -> enumValue<LearningGoal>(raw) }
            ?.take(2)
            ?.toSet()
            .orEmpty()
        return LearningProfile(
            version = if (storedVersion <= LearningProfile.SCHEMA_VERSION) LearningProfile.SCHEMA_VERSION else storedVersion,
            onboardingCompleted = preferences.getBoolean(KEY_COMPLETED, false),
            skillLevel = enumValue(preferences.getString(KEY_SKILL, null)) ?: SkillLevel.BEGINNER,
            goals = goals.ifEmpty { setOf(LearningGoal.BASIC_CHORDS, LearningGoal.CHORD_TRANSITIONS) },
            dailyTargetMinutes = preferences.getInt(KEY_DAILY_MINUTES, 5).coerceIn(1, 180),
            preferredExperienceMode = enumValue(preferences.getString(KEY_EXPERIENCE, null)) ?: ExperienceMode.BEGINNER,
            createdAt = preferences.getLong(KEY_CREATED_AT, 0L),
            updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?): T? =
        enumValues<T>().firstOrNull { it.name == raw }

    private companion object {
        const val FILE_NAME = "learning_profile_v1"
        const val KEY_VERSION = "version"
        const val KEY_COMPLETED = "onboarding_completed"
        const val KEY_SKILL = "skill_level"
        const val KEY_GOALS = "goals"
        const val KEY_DAILY_MINUTES = "daily_target_minutes"
        const val KEY_EXPERIENCE = "experience_mode"
        const val KEY_CREATED_AT = "created_at"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
