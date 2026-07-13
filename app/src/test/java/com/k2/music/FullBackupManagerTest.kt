package com.k2.music

import android.content.SharedPreferences
import com.k2.music.ui.learning.LearningGoal
import com.k2.music.ui.learning.LearningProfile
import com.k2.music.ui.learning.LearningProfileStore
import com.k2.music.ui.learning.SkillLevel
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.preferences.ExperienceMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FullBackupManagerTest {
    @Test
    fun backupPreviewOverwriteAndRepeatedMergeAreCompleteAndIdempotent() {
        val fixture = Fixture()
        fixture.learning.save(
            LearningProfile(
                onboardingCompleted = true,
                skillLevel = SkillLevel.BASIC,
                goals = setOf(LearningGoal.BASIC_CHORDS, LearningGoal.CHORD_TRANSITIONS),
                dailyTargetMinutes = 10,
                preferredExperienceMode = ExperienceMode.PROFESSIONAL,
            ),
        )
        fixture.user.replaceFavorites(listOf("C", "G"))
        fixture.user.replaceHistory(listOf("Am", "C"))
        fixture.custom.save(CustomVoicing("custom", "C", "我的 C", intArrayOf(-1, 3, 2, 0, 1, 0), intArrayOf(0, 3, 2, 0, 1, 0), 1, "", 1L))
        fixture.progressions.replaceAll(listOf(progression("p1")))
        val session = PracticeSession.recorded(
            "s1", 1L, 2L, PracticeSession.Type.TWO_CHORD_TRANSITION, listOf("C", "G"), 60,
            "4/4", PracticeSession.SwitchMode.EACH_MEASURE, 60, 60, 1, 1, 0, 1,
            "p1", true,
        )
        fixture.sessions.replaceAll(listOf(session))
        fixture.attempts.replaceAll(listOf(attempt("a1", "s1")))
        fixture.aiPreferences.edit().putString("api_key_ciphertext_v1", "SUPER_SECRET_SENTINEL").commit()

        val output = ByteArrayOutputStream()
        fixture.manager.writeBackup(output, 1234L)
        val bytes = output.toByteArray()
        assertFalse(readZipText(bytes).contains("SUPER_SECRET_SENTINEL"))
        val preview = fixture.manager.preview(ByteArrayInputStream(bytes))
        assertEquals(2, preview.favoriteCount)
        assertEquals(1, preview.customVoicingCount)
        assertEquals(1, preview.progressionCount)
        assertEquals(1, preview.practiceSessionCount)
        assertEquals(1, preview.transitionAttemptCount)

        fixture.user.replaceFavorites(emptyList())
        fixture.custom.replaceAll(emptyList())
        fixture.progressions.replaceAll(emptyList())
        fixture.sessions.replaceAll(emptyList())
        fixture.attempts.replaceAll(emptyList())
        fixture.manager.restore(ByteArrayInputStream(bytes), RestoreMode.OVERWRITE, true)
        assertEquals(listOf("C", "G"), fixture.user.favorites())
        assertEquals(1, fixture.sessions.list().size)
        assertEquals("p1", fixture.sessions.list().single().sourceProgressionId)
        assertTrue(fixture.sessions.list().single().useProgressionRhythm)
        assertEquals(1, fixture.attempts.list().size)

        val repeated = fixture.manager.restore(ByteArrayInputStream(bytes), RestoreMode.MERGE, false)
        assertEquals(1, fixture.sessions.list().size)
        assertEquals(1, fixture.attempts.list().size)
        assertTrue(repeated.skippedItems >= 2)
    }

    @Test(expected = BackupFormatException::class)
    fun unsafeZipPathIsRejectedBeforeRestore() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../manifest.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        Fixture().manager.preview(ByteArrayInputStream(output.toByteArray()))
    }

    @Test
    fun historyMergeUsesNewestTimestampAcrossBackupAndLocalData() {
        val source = Fixture()
        source.user.replaceHistoryEntries(
            listOf(UserChordStore.HistoryEntry("G", 3L), UserChordStore.HistoryEntry("C", 1L)),
        )
        val output = ByteArrayOutputStream()
        source.manager.writeBackup(output, 10L)

        val target = Fixture()
        target.user.replaceHistoryEntries(
            listOf(UserChordStore.HistoryEntry("C", 4L), UserChordStore.HistoryEntry("Am", 2L)),
        )
        target.manager.restore(ByteArrayInputStream(output.toByteArray()), RestoreMode.MERGE, false)

        assertEquals(listOf("C", "G", "Am"), target.user.history())
        assertEquals(listOf(4L, 3L, 2L), target.user.historyEntries().map { it.timestampEpochMillis })
    }

    @Test
    fun checksumMismatchIsRejectedWithoutChangingLocalData() {
        val fixture = Fixture()
        fixture.user.replaceFavorites(listOf("C"))
        val output = ByteArrayOutputStream()
        fixture.manager.writeBackup(output, 10L)
        val corrupted = rewriteZip(output.toByteArray()) { name, bytes ->
            if (name == "favorites.json") "[\"G\"]".toByteArray() else bytes
        }

        fixture.user.replaceFavorites(listOf("Am"))
        try {
            fixture.manager.restore(ByteArrayInputStream(corrupted), RestoreMode.OVERWRITE, true)
            fail("checksum mismatch should be rejected")
        } catch (_: BackupFormatException) {
            assertEquals(listOf("Am"), fixture.user.favorites())
        }
    }

    private fun readZipText(bytes: ByteArray): String = buildString {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                append(entry.name).append('\n')
                append(String(zip.readBytes(), Charsets.UTF_8)).append('\n')
            }
        }
    }

    private fun rewriteZip(bytes: ByteArray, transform: (String, ByteArray) -> ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { target ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { source ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    target.putNextEntry(ZipEntry(entry.name))
                    target.write(transform(entry.name, source.readBytes()))
                    target.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun progression(id: String) = ChordProgression(
        id, "进行", "C", TimeSignature.FOUR_FOUR, 60, true,
        listOf(ProgressionStep("C", "", 4.0, "", 0), ProgressionStep("G", "", 4.0, "", 1)),
        1L, 1L, "",
    )

    private fun attempt(id: String, sessionId: String) = TransitionAttempt(
        id, sessionId, 2L, "C", "G", "", "", 60, "4/4",
        PracticeSession.SwitchMode.EACH_MEASURE, true, 100L, PracticeSession.Type.TWO_CHORD_TRANSITION,
    )

    private class Fixture {
        private val directory = Files.createTempDirectory("k2-backup-test").toFile()
        private val appPrefs = MemoryPreferences()
        private val learningPrefs = MemoryPreferences()
        private val userPrefs = MemoryPreferences()
        private val customPrefs = MemoryPreferences()
        val aiPreferences = MemoryPreferences()
        val app = AppPreferences(appPrefs)
        val learning = LearningProfileStore(learningPrefs)
        val user = UserChordStore(userPrefs, null)
        val custom = CustomVoicingStore(customPrefs)
        val progressions = ProgressionStore(directory.resolve("progressions.bin"))
        val drafts = ProgressionStore(directory.resolve("drafts.bin"))
        val practicePreferences = PracticePreferencesStore(directory.resolve("practice-preferences.bin"))
        val sessions = PracticeRecordStore(directory.resolve("sessions.bin"))
        val attempts = TransitionAttemptStore(directory.resolve("attempts.bin"))
        val ai = AiSettingsStore(aiPreferences)
        val manager = FullBackupManager(
            app, learning, user, custom, progressions, drafts, practicePreferences, sessions, attempts, ai,
        )
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
        override fun putString(key: String?, value: String?) = apply { changes[requireNotNull(key)] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { changes[requireNotNull(key)] = values?.toSet() }
        override fun putInt(key: String?, value: Int) = apply { changes[requireNotNull(key)] = value }
        override fun putLong(key: String?, value: Long) = apply { changes[requireNotNull(key)] = value }
        override fun putFloat(key: String?, value: Float) = apply { changes[requireNotNull(key)] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { changes[requireNotNull(key)] = value }
        override fun remove(key: String?) = apply { changes[requireNotNull(key)] = REMOVED }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { applyChanges(); return true }
        override fun apply() = applyChanges()
        private fun applyChanges() {
            if (clear) target.clear()
            changes.forEach { (key, value) -> if (value === REMOVED) target.remove(key) else target[key] = value }
        }
        private companion object { val REMOVED = Any() }
    }
}
