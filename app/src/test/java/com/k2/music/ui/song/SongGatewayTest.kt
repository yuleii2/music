package com.k2.music.ui.song

import com.k2.music.ChordRepository
import com.k2.music.song.RepositorySongChordResolver
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongPracticeRun
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongSheetParser
import com.k2.music.song.SongParseLineRole
import com.k2.music.song.SongTransition
import com.k2.music.song.UserReportedDifficulty
import com.k2.music.song.UserReportedDifficultyStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongGatewayTest {
    @Test
    fun importDraftSurvivesGatewayRecreationAndSavedSongAppearsInLibrary() = runTest {
        withGateway { directory, gateway, projectStore, runStore, difficultyStore ->
            val source = "曲名：本地练习曲\n[主歌]\n| C G | Am F |"
            val parsed = gateway.parseImport("", "测试作者", source, "4/4")
            assertEquals("本地练习曲", parsed.title)
            assertEquals(4, parsed.parseResult.chordEventCount)

            val recreated = gateway(directory, projectStore, runStore, difficultyStore, 200L)
            val restored = recreated.importDraft()
            assertEquals(source, restored?.originalText)
            assertEquals(parsed.parseResult.sections, restored?.parseResult?.sections)

            recreated.setImportLineRole(3, SongParseLineRole.CHORDS)
            val correctedAfterRecreation = gateway(directory, projectStore, runStore, difficultyStore, 250L).importDraft()
            assertEquals(SongParseLineRole.CHORDS, correctedAfterRecreation?.lineOverrides?.get(3))
            assertEquals(source, correctedAfterRecreation?.originalText)

            val project = gateway(directory, projectStore, runStore, difficultyStore, 300L).saveImportDraft()
            assertEquals(source, project.originalText)
            assertEquals(1, projectStore.list().size)
            val library = recreated.library()
            assertEquals(listOf(project.id), library.all.map { it.project.id })
            assertEquals(listOf(project.id), library.incomplete.map { it.project.id })
            assertTrue(library.recent.isEmpty())
            assertNull(recreated.importDraft())
        }
    }

    @Test
    fun recentStatusDetailSearchAndDeleteUseAllThreeIndependentStores() = runTest {
        withGateway { _, gateway, projectStore, runStore, difficultyStore ->
            gateway.parseImport("可搜索的歌", "离线作者", "[副歌]\n| C | G |", "4/4")
            val project = gateway.saveImportDraft().copy(originalKey = "C")
            projectStore.save(project)
            runStore.save(
                SongPracticeRun(
                    "run", project.id, project.sections.single().id, SongPracticeMode.PERFORMANCE,
                    66, 0, 0, 300L, 360L, 60, false, listOf(SongTransition("C", "G")),
                ),
            )
            difficultyStore.save(
                UserReportedDifficulty(
                    "difficulty", project.id, project.sections.single().id, "C", "G", 360L,
                    false, "用户主动标记",
                ),
            )

            val library = gateway.library("离线")
            assertEquals(1, library.all.size)
            assertEquals("待继续", library.all.single().practiceStatus)
            assertEquals(66, library.all.single().recentBpm)
            assertEquals(project.id, library.recent.single().project.id)

            val detail = gateway.detail(project.id)
            assertNotNull(detail)
            assertEquals(1, detail?.runs?.size)
            assertEquals(1, detail?.difficulties?.size)
            assertFalse(project.sections.single().id in requireNotNull(detail).unpracticedSectionIds)

            assertTrue(gateway.deleteProject(project.id))
            assertNull(projectStore.read(project.id))
            assertTrue(runStore.forSong(project.id).isEmpty())
            assertTrue(difficultyStore.forSong(project.id).isEmpty())
            assertFalse(gateway.deleteProject(project.id))
        }
    }

    @Test
    fun manualProjectIsNotPersistedUntilExplicitSave() = runTest {
        withGateway { _, gateway, projectStore, _, _ ->
            val manual = gateway.createManual()
            assertTrue(projectStore.list().isEmpty())

            gateway.saveProject(manual.copy(title = "手动曲谱", originalKey = "G"))

            assertEquals("手动曲谱", projectStore.read(manual.id)?.title)
            assertEquals(100L, projectStore.read(manual.id)?.updatedAt)
        }
    }

    private suspend fun withGateway(
        block: suspend (
            File,
            DefaultSongGateway,
            SongProjectStore,
            SongPracticeRunStore,
            UserReportedDifficultyStore,
        ) -> Unit,
    ) {
        val directory = Files.createTempDirectory("song-gateway-test").toFile()
        try {
            val projects = SongProjectStore(SongProjectStore.defaultFile(directory))
            val runs = SongPracticeRunStore(SongPracticeRunStore.defaultFile(directory))
            val difficulties = UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(directory))
            block(directory, gateway(directory, projects, runs, difficulties, 100L), projects, runs, difficulties)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun gateway(
        directory: File,
        projects: SongProjectStore,
        runs: SongPracticeRunStore,
        difficulties: UserReportedDifficultyStore,
        now: Long,
    ) = DefaultSongGateway(
        projects,
        runs,
        difficulties,
        SongSheetParser(RepositorySongChordResolver(ChordRepository())),
        SongImportDraftStore(SongImportDraftStore.defaultFile(directory)),
        ioDispatcher = Dispatchers.Unconfined,
        clock = { now },
    )
}
