package com.k2.music.song

import com.k2.music.LocalStoreException
import com.k2.music.MusicTheoryUtils
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongStoresTest {
    @Test
    fun projectRoundTripPreservesOriginalTextSchemaTimingAndPinnedVoicing() = withDirectory { directory ->
        val file = SongProjectStore.defaultFile(directory)
        val store = SongProjectStore(file)
        val project = project("song-1", "  [主歌]\n[C]原文不能丢失  \n")

        store.save(project)

        assertEquals(project, store.read(project.id))
        assertEquals(project.originalText, store.read(project.id)?.originalText)
        assertEquals(SongProjectStore.SCHEMA_VERSION, project.schemaVersion)
        assertEquals(SongTimingState.EXPLICIT_BEATS, project.timingState)
        assertTrue(project.canUsePrecisePlayback)
        assertEquals("voicing-C", project.sections.single().rows.single().chordEvents.single().selectedVoicingId)
        assertEquals(MusicTheoryUtils.AccidentalPreference.FLATS, store.read(project.id)?.accidentalPreference)
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun corruptPrimaryFallsBackToBackupWithoutOverwritingItAndBothCorruptionsAreReported() =
        withDirectory { directory ->
            val file = SongProjectStore.defaultFile(directory)
            val backup = File(file.path + ".bak")
            val store = SongProjectStore(file)
            val first = project("first", "first-original")
            val second = project("second", "second-original")
            store.replaceAll(listOf(first))
            store.replaceAll(listOf(second))
            assertTrue(backup.isFile)

            file.writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(listOf(first), store.list())
            assertEquals(byteArrayOf(1, 2, 3).toList(), file.readBytes().toList())

            backup.writeBytes(byteArrayOf(4, 5, 6))
            val error = assertThrows(LocalStoreException::class.java) { store.list() }
            assertTrue(error.message.orEmpty().contains("无法读取本地曲谱"))
        }

    @Test
    fun duplicateIdsAreRejectedBeforeReplacementAndExistingDataSurvives() = withDirectory { directory ->
        val store = SongProjectStore(SongProjectStore.defaultFile(directory))
        val original = project("original", "kept")
        store.save(original)
        val duplicate = project("duplicate", "one")

        assertThrows(IllegalArgumentException::class.java) {
            store.replaceAll(listOf(duplicate, duplicate.copy(title = "different")))
        }
        assertEquals(listOf(original), store.list())
    }

    @Test
    fun practiceRunsAndUserDifficultiesUseSeparateIdempotentStores() = withDirectory { directory ->
        val runStore = SongPracticeRunStore(SongPracticeRunStore.defaultFile(directory))
        val difficultyStore = UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(directory))
        val run = SongPracticeRun(
            "run-1", "song-1", "section-1", SongPracticeMode.PERFORMANCE, 72, 2, 3,
            100L, 160L, 60, true, listOf(SongTransition("C", "G")),
            selectedVoicingIds = mapOf("event-1" to "voicing-1"),
        )
        val difficulty = UserReportedDifficulty(
            "difficulty-1", "song-1", "section-1", "C", "G", 160L, false, "换慢了",
        )

        runStore.save(run)
        runStore.save(run)
        difficultyStore.save(difficulty)
        difficultyStore.save(difficulty)

        assertEquals(1, runStore.list().size)
        assertEquals(run, runStore.read(run.id))
        assertEquals(mapOf("event-1" to "voicing-1"), runStore.read(run.id)?.selectedVoicingIds)
        assertEquals(1, difficultyStore.list().size)
        assertEquals(true, difficultyStore.setResolved(difficulty.id, true)?.resolved)
        assertNotNull(File(runStore.file().path))
        assertEquals(3, SongPracticeRunStore.SCHEMA_VERSION)
        assertEquals(1, UserReportedDifficultyStore.SCHEMA_VERSION)
    }

    @Test
    fun versionOneProjectMigratesToCurrentSchemaWithoutLosingFields() = withDirectory { directory ->
        val file = SongProjectStore.defaultFile(directory)
        writeLegacyVersionOneProject(file)

        val store = SongProjectStore(file)
        val migrated = store.list().single()

        assertEquals("legacy-song", migrated.id)
        assertEquals("旧版曲谱", migrated.title)
        assertEquals(SongLimits.PROJECT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(MusicTheoryUtils.AccidentalPreference.AUTO, migrated.accidentalPreference)
        store.save(migrated.copy(title = "已迁移"))
        DataInputStream(FileInputStream(file)).use { input ->
            assertEquals(0x4B325350, input.readInt())
            assertEquals(SongProjectStore.SCHEMA_VERSION, input.readInt())
        }
    }

    @Test
    fun versionOnePracticeRunRestoresSafeLoopAndDisplayDefaults() = withDirectory { directory ->
        val file = SongPracticeRunStore.defaultFile(directory)
        fun DataOutputStream.text(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
        FileOutputStream(file).use { stream ->
            DataOutputStream(stream).use { output ->
                output.writeInt(0x4B325352)
                output.writeInt(1)
                output.writeInt(1)
                output.text("legacy-run")
                output.text("legacy-song")
                output.text("legacy-section")
                output.text(SongPracticeMode.PERFORMANCE.name)
                output.writeInt(60)
                output.writeInt(0)
                output.writeInt(0)
                output.writeLong(10L)
                output.writeLong(20L)
                output.writeInt(10)
                output.writeBoolean(true)
                output.writeInt(0)
            }
        }

        val migrated = SongPracticeRunStore(file).list().single()

        assertTrue(migrated.loopEnabled)
        assertTrue(migrated.showFretboard)
        assertTrue(migrated.selectedVoicingIds.isEmpty())
    }

    private fun project(id: String, originalText: String): SongProject {
        val event = SongChordEvent(
            "event-$id", "C", "C", 0, 4.0, "voicing-C", 0, 0,
        )
        val row = SongRow("row-$id", "歌词", "C", listOf(event), 0)
        val section = SongSection("section-$id", "主歌", SongSectionType.VERSE, 0, 2, listOf(row))
        return SongProject(
            id = id,
            title = "测试曲谱 $id",
            artist = "作者",
            originalText = originalText,
            originalKey = "C",
            transposeSemitones = 0,
            capoFret = 0,
            bpm = 72,
            timeSignature = "4/4",
            timingState = SongTimingState.EXPLICIT_BEATS,
            sections = listOf(section),
            notes = "备注",
            createdAt = 100L,
            updatedAt = 100L,
            accidentalPreference = MusicTheoryUtils.AccidentalPreference.FLATS,
        )
    }

    private fun writeLegacyVersionOneProject(file: File) {
        fun DataOutputStream.text(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
        FileOutputStream(file).use { stream ->
            DataOutputStream(stream).use { output ->
                output.writeInt(0x4B325350)
                output.writeInt(1)
                output.writeInt(1)
                output.writeInt(1)
                output.writeInt(1)
                output.text("legacy-song")
                output.text("旧版曲谱")
                output.text("作者")
                output.text("| C | G |")
                output.text("C")
                output.writeInt(0)
                output.writeInt(0)
                output.writeInt(60)
                output.text("4/4")
                output.text(SongTimingState.UNTYPED.name)
                output.text("旧备注")
                output.writeLong(10L)
                output.writeLong(10L)
                output.writeInt(0)
            }
        }
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("song-store-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
