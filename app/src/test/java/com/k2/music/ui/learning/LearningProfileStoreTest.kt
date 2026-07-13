package com.k2.music.ui.learning

import android.content.SharedPreferences
import com.k2.music.ui.preferences.ExperienceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningProfileStoreTest {
    @Test
    fun profileAndOnboardingCompletionPersistAcrossStoreRecreation() {
        val preferences = MemoryPreferences()
        val first = LearningProfileStore(preferences)
        first.save(
            LearningProfile(
                onboardingCompleted = true,
                skillLevel = SkillLevel.BASIC,
                goals = setOf(LearningGoal.CHORD_TRANSITIONS, LearningGoal.SONG_ACCOMPANIMENT),
                dailyTargetMinutes = 10,
                preferredExperienceMode = ExperienceMode.PROFESSIONAL,
            ),
        )

        val restored = LearningProfileStore(preferences).profile.value
        assertTrue(restored.onboardingCompleted)
        assertEquals(SkillLevel.BASIC, restored.skillLevel)
        assertEquals(10, restored.dailyTargetMinutes)
        assertEquals(ExperienceMode.PROFESSIONAL, restored.preferredExperienceMode)
        assertEquals(2, restored.goals.size)
    }

    @Test
    fun skipUsesDocumentedBeginnerDefaults() {
        val profile = LearningProfileStore(MemoryPreferences()).skip()
        assertEquals(SkillLevel.BEGINNER, profile.skillLevel)
        assertEquals(5, profile.dailyTargetMinutes)
        assertEquals(ExperienceMode.BEGINNER, profile.preferredExperienceMode)
        assertTrue(LearningGoal.BASIC_CHORDS in profile.goals)
        assertTrue(LearningGoal.CHORD_TRANSITIONS in profile.goals)
    }
}

private class MemoryPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            changes[requireNotNull(key)] = values?.toSet()
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = REMOVED }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean { applyChanges(); return true }
        override fun apply() = applyChanges()
        private fun applyChanges() {
            if (clear) target.clear()
            changes.forEach { (key, value) -> if (value === REMOVED) target.remove(key) else target[key] = value }
        }
        private companion object { val REMOVED = Any() }
    }
}
