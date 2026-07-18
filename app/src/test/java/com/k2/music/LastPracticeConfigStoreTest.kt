package com.k2.music

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastPracticeConfigStoreTest {
    @Test
    fun roundTripPreservesEveryRestorableFieldAndCreatesBackup() {
        val directory = Files.createTempDirectory("last-practice-test").toFile()
        try {
            val file = File(directory, "last-practice-config-v1.bin")
            val store = LastPracticeConfigStore(file)
            val first = config(bpm = 72, accent = false, allowBarre = false, maxFret = 5)
            assertEquals(first, store.save(first))
            assertEquals(first, store.load())

            val second = config(bpm = 84, accent = true, allowBarre = true, maxFret = 17)
            store.save(second)
            assertEquals(second, store.load())
            assertTrue(File(file.path + ".bak").isFile)
            assertEquals(1, LastPracticeConfigStore.SCHEMA_VERSION)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun config(bpm: Int, accent: Boolean, allowBarre: Boolean, maxFret: Int) =
        LastPracticeConfig(
            PracticeSession.Type.PROGRESSION_LOOP,
            listOf("C", "G/B", "Am", "Fmaj7"),
            135,
            bpm,
            "6/8",
            PracticeSession.SwitchMode.EACH_BEAT,
            accent,
            allowBarre,
            maxFret,
            "progression-42",
            true,
        )
}
