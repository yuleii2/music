package com.k2.music

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2.music.ui.gateway.DefaultExportGateway
import com.k2.music.ui.gateway.ExportRequestUi
import com.k2.music.ui.gateway.ExportScopeUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.k2.music.ui.learning.LearningProfileStore
import com.k2.music.ui.preferences.AppPreferences
import java.io.FileInputStream
import java.io.FileOutputStream
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.UserReportedDifficultyStore

@RunWith(AndroidJUnit4::class)
class CoreExportInstrumentationTest {
    @Test
    fun fullBackupRestoresRealPracticeFilesWithoutDoubling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ChordRepository()
        val user = UserChordStore(context, repository)
        val custom = CustomVoicingStore(context)
        val progressions = ProgressionStore(ProgressionStore.defaultFile(context.filesDir))
        val drafts = ProgressionStore(java.io.File(context.filesDir, "progression-drafts-v1.bin"))
        val practicePreferences = PracticePreferencesStore(PracticePreferencesStore.defaultFile(context.filesDir))
        val sessions = PracticeRecordStore(PracticeRecordStore.defaultFile(context.filesDir))
        val attempts = TransitionAttemptStore(TransitionAttemptStore.defaultFile(context.filesDir))
        val sessionId = "device-backup-session"
        val attemptId = "device-backup-attempt"
        sessions.delete(sessionId)
        attempts.deleteSession(sessionId)
        val session = PracticeSession.recorded(
            sessionId, 1L, 2L, PracticeSession.Type.TWO_CHORD_TRANSITION, listOf("C", "G"), 60,
            "4/4", PracticeSession.SwitchMode.EACH_MEASURE, 60, 60, 1, 1, 0, 1,
        )
        val attempt = TransitionAttempt(
            attemptId, sessionId, 2L, "C", "G", "", "", 60, "4/4",
            PracticeSession.SwitchMode.EACH_MEASURE, true, 100L, PracticeSession.Type.TWO_CHORD_TRANSITION,
        )
        sessions.save(session)
        attempts.save(attempt)
        val manager = FullBackupManager(
            AppPreferences(context), LearningProfileStore(context), user, custom, progressions, drafts,
            practicePreferences, sessions, attempts, AiSettingsStore(context),
            SongProjectStore(SongProjectStore.defaultFile(context.filesDir)),
            SongPracticeRunStore(SongPracticeRunStore.defaultFile(context.filesDir)),
            UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(context.filesDir)),
        )
        val file = java.io.File(context.cacheDir, "device-full-backup.zip")
        try {
            FileOutputStream(file).use { manager.writeBackup(it, 10L) }
            val preview = FileInputStream(file).use(manager::preview)
            assertTrue(preview.practiceSessionCount >= 1)
            sessions.delete(sessionId)
            attempts.deleteSession(sessionId)
            FileInputStream(file).use { manager.restore(it, RestoreMode.MERGE, false) }
            FileInputStream(file).use { manager.restore(it, RestoreMode.MERGE, false) }
            assertEquals(session, sessions.read(sessionId))
            assertEquals(1, attempts.forSession(sessionId).count { it.id == attemptId })
        } finally {
            sessions.delete(sessionId)
            attempts.deleteSession(sessionId)
            file.delete()
        }
    }

    @Test
    fun customVoicingsAreIncludedByTheComposeExportGateway() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ChordRepository()
        val store = CustomVoicingStore(context)
        val id = "device-export-custom"
        store.delete(id)
        try {
            store.save(
                CustomVoicing(
                    id,
                    "C",
                    "设备导出自定义指法",
                    intArrayOf(-1, 3, 5, 5, 5, 3),
                    intArrayOf(0, 1, 3, 3, 3, 1),
                    3,
                    "设备回归",
                    System.currentTimeMillis(),
                ),
            )
            val chord = checkNotNull(repository.find("C").chord)
            val gateway = DefaultExportGateway(
                context,
                repository,
                store,
                UserChordStore(context, repository),
            )

            assertEquals(
                chord.voicings.size + 1,
                gateway.count(ExportRequestUi(ExportScopeUi.CHORD_ALL, listOf("C"))),
            )
        } finally {
            store.delete(id)
        }
    }

    @Test
    fun exportedInfoTextKeepsItsLeftMargin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chord = checkNotNull(ChordRepository().find("C").chord)
        val bitmap = VoicingImageExporter.render(context, chord, chord.voicings[0])
        try {
            assertFalse(
                "Info text leaked into the left crop margin",
                containsDarkPixel(bitmap, 0, 80, 1640, 1750),
            )
            assertTrue(
                "Info heading was not rendered in its intended content area",
                containsDarkPixel(bitmap, 100, 650, 1640, 1750),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun containsDarkPixel(
        bitmap: android.graphics.Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): Boolean {
        val safeRight = minOf(bitmap.width, right)
        val safeBottom = minOf(bitmap.height, bottom)
        for (y in maxOf(0, top) until safeBottom step 2) {
            for (x in maxOf(0, left) until safeRight step 2) {
                val color = bitmap.getPixel(x, y)
                if (Color.red(color) < 100 && Color.green(color) < 100 && Color.blue(color) < 100) {
                    return true
                }
            }
        }
        return false
    }
}
