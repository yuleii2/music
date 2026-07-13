package com.k2.music.ui.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MotionLevel { FULL, REDUCED, OFF }

enum class ExperienceMode { BEGINNER, PROFESSIONAL }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val motionLevel: MotionLevel = MotionLevel.FULL,
    val experienceMode: ExperienceMode = ExperienceMode.BEGINNER,
    val dynamicColor: Boolean = false,
    val recentToolId: String? = null,
)

/** Independent, backward-compatible UI preferences for the Compose frontend. */
class AppPreferences(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(value: ThemeMode) = update(KEY_THEME, value.name)

    fun setMotionLevel(value: MotionLevel) = update(KEY_MOTION, value.name)

    fun setExperienceMode(value: ExperienceMode) = update(KEY_EXPERIENCE, value.name)

    fun setDynamicColor(value: Boolean) {
        preferences.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }
        _settings.value = readSettings()
    }

    fun setRecentTool(value: String) {
        preferences.edit { putString(KEY_RECENT_TOOL, value) }
        _settings.value = readSettings()
    }

    private fun update(key: String, value: String) {
        preferences.edit { putString(key, value) }
        _settings.value = readSettings()
    }

    private fun readSettings(): AppSettings = AppSettings(
        themeMode = enumValue(preferences.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        motionLevel = enumValue(preferences.getString(KEY_MOTION, null), MotionLevel.FULL),
        experienceMode = enumValue(preferences.getString(KEY_EXPERIENCE, null), ExperienceMode.BEGINNER),
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, false),
        recentToolId = preferences.getString(KEY_RECENT_TOOL, null),
    )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val FILE_NAME = "compose_ui_preferences"
        const val KEY_THEME = "theme"
        const val KEY_MOTION = "motion"
        const val KEY_EXPERIENCE = "experience"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_RECENT_TOOL = "recent_tool"
    }
}
