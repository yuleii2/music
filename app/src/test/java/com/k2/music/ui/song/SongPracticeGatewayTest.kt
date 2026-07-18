package com.k2.music.ui.song

import com.k2.music.ChordRepository
import com.k2.music.PracticePreferencesStore
import com.k2.music.TransitionAttemptStore
import com.k2.music.TransitionAttempt
import com.k2.music.PracticeSession
import com.k2.music.song.RepositorySongChordResolver
import com.k2.music.song.SongArrangementEngine
import com.k2.music.song.SongChordEvent
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.SongProject
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongRow
import com.k2.music.song.SongSection
import com.k2.music.song.SongSectionType
import com.k2.music.song.SongSheetParser
import com.k2.music.song.SongTimingState
import com.k2.music.song.SongTransition
import com.k2.music.song.SongTransitionExtractor
import com.k2.music.song.UserReportedDifficultyStore
import com.k2.music.ui.gateway.ProgressionGateway
import com.k2.music.ui.model.ProgressionPlaybackMode
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionSummaryUi
import com.k2.music.ui.model.ProgressionPresetUi
import com.k2.music.ui.model.ProgressionUiModel
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPracticeGatewayTest {
    @Test
    fun preparationUsesSectionRepeatsExplicitDurationsAndOptionalPairSelection() = runTest {
        withFixture { fixture ->
            val project = fixture.project(timed = true, repeat = 2)
            fixture.projects.save(project)

            val whole = fixture.gateway.preparePractice(project.id, project.sections.single().id)
            assertTrue(whole.preciseTiming)
            assertEquals(listOf(2.0, 4.0, 2.0, 4.0), whole.progression.steps.map { it.beats })
            assertEquals(4, whole.progression.steps.size)
            assertEquals(listOf(SongTransition("C", "G"), SongTransition("G", "C")), whole.transitions)

            val pair = fixture.gateway.preparePractice(
                project.id,
                project.sections.single().id,
                SongTransition("G", "C"),
            )
            assertEquals(listOf("G", "C"), pair.progression.steps.map { it.chordSymbol })
            assertFalse(pair.preciseTiming)
        }
    }

    @Test
    fun untypedPreparationUsesManualTimingMessageInsteadOfClaimingPreciseSync() = runTest {
        withFixture { fixture ->
            val project = fixture.project(timed = false, repeat = 1)
            fixture.projects.save(project)

            val preparation = fixture.gateway.preparePractice(project.id, project.sections.single().id)

            assertFalse(preparation.preciseTiming)
            assertTrue(preparation.timingMessage.contains("不可靠"))
            assertTrue(preparation.timingMessage.contains("手动"))
        }
    }

    @Test
    fun pairPreparationRejectsChordsThatExistButAreNotAdjacentInThatDirection() = runTest {
        withFixture { fixture ->
            val base = fixture.project(timed = true, repeat = 1)
            val section = base.sections.single()
            val row = section.rows.single()
            val project = base.copy(
                sections = listOf(
                    section.copy(
                        rows = listOf(
                            row.copy(
                                chordEvents = listOf(
                                    row.chordEvents[0],
                                    row.chordEvents[1],
                                    SongChordEvent("event-f", "F", "F", null, 2.0, null, 2, 2),
                                ),
                                rawChordText = "C G F",
                            ),
                        ),
                    ),
                ),
            )
            fixture.projects.save(project)

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    fixture.gateway.preparePractice(project.id, section.id, SongTransition("C", "F"))
                }
            }
        }
    }

    @Test
    fun performanceWritesOnlyRunAndCheckedDifficultyWhileGuidedRunWritesNoDifficultyOrAttempt() = runTest {
        withFixture { fixture ->
            val baseProject = fixture.project(timed = true, repeat = 1)
            val project = baseProject.copy(
                sections = baseProject.sections.map { section ->
                    section.copy(rows = section.rows.map { row ->
                        row.copy(chordEvents = row.chordEvents.map { event ->
                            if (event.id == "event-c") event.copy(selectedVoicingId = "voicing-old") else event
                        })
                    })
                },
            )
            fixture.projects.save(project)
            val transition = fixture.gateway.preparePractice(project.id, project.sections.single().id).transitions.first()

            fixture.gateway.savePracticeRun(
                project.id,
                project.sections.single().id,
                SongPracticeMode.PERFORMANCE,
                72,
                100L,
                160L,
                60,
                true,
                listOf(transition),
            )

            assertEquals(1, fixture.runs.list().size)
            assertEquals(listOf(transition), fixture.runs.list().single().reportedDifficultTransitions)
            assertEquals(1, fixture.difficulties.list().size)
            assertTrue(fixture.attempts.list().isEmpty())

            fixture.gateway.savePracticeRun(
                project.id,
                project.sections.single().id,
                SongPracticeMode.GUIDED_TRANSITION,
                60,
                200L,
                220L,
                20,
                false,
                emptyList(),
                runId = "guided-session",
            )
            assertEquals(2, fixture.runs.list().size)
            assertEquals(1, fixture.difficulties.list().size)
            assertTrue(fixture.attempts.list().isEmpty())

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    fixture.gateway.savePracticeRun(
                        project.id, project.sections.single().id, SongPracticeMode.GUIDED_TRANSITION,
                        60, 300L, 320L, 20, false, listOf(transition),
                    )
                }
            }
        }
    }

    @Test
    fun transitionAttemptStorePersistsOptionalSongAndSectionProvenance() = runTest {
        withFixture { fixture ->
            val attempt = TransitionAttempt(
                "attempt", "session", 100L, "C", "G", "", "", 60, "4/4",
                PracticeSession.SwitchMode.EACH_MEASURE, true, 10L,
                PracticeSession.Type.PROGRESSION_LOOP, "song", "section",
            )

            fixture.attempts.save(attempt)

            assertEquals("song", fixture.attempts.read("attempt").songId)
            assertEquals("section", fixture.attempts.read("attempt").sectionId)
            assertEquals(2, TransitionAttemptStore.SCHEMA_VERSION)
        }
    }

    @Test
    fun accompanimentGoalBuildsPriorityTasksAndContinueRestoresStoredConfiguration() = runTest {
        withFixture { fixture ->
            val baseProject = fixture.project(timed = true, repeat = 1)
            val project = baseProject.copy(
                sections = baseProject.sections.map { section ->
                    section.copy(rows = section.rows.map { row ->
                        row.copy(chordEvents = row.chordEvents.map { event ->
                            if (event.id == "event-c") event.copy(selectedVoicingId = "voicing-old") else event
                        })
                    })
                },
            )
            fixture.projects.save(project)
            val transition = SongTransition("C", "G")
            fixture.gateway.savePracticeRun(
                songId = project.id,
                sectionId = project.sections.single().id,
                mode = SongPracticeMode.PERFORMANCE,
                bpm = 72,
                startedAt = 100L,
                endedAt = 160L,
                actualDurationSeconds = 60,
                completed = true,
                difficultTransitions = listOf(transition),
                loopEnabled = false,
                showFretboard = false,
            )

            assertTrue(fixture.gateway.homeTasks(false).isEmpty())
            val tasks = fixture.gateway.homeTasks(true)
            assertEquals(SongHomeTaskType.CONTINUE_RECENT, tasks[0].type)
            assertEquals(SongHomeTaskType.REVIEW_REPORTED_DIFFICULTY, tasks[1].type)
            assertEquals(72, tasks[0].bpm)
            assertFalse(tasks[0].loopEnabled)
            assertFalse(tasks[0].showFretboard)
            assertEquals(transition, tasks[1].transition)
            assertEquals(mapOf("event-c" to "voicing-old"), tasks[0].selectedVoicingIds)

            val changed = requireNotNull(fixture.projects.read(project.id)).copy(
                sections = project.sections.map { section ->
                    section.copy(rows = section.rows.map { row ->
                        row.copy(chordEvents = row.chordEvents.map { event ->
                            if (event.id == "event-c") event.copy(selectedVoicingId = "voicing-new") else event
                        })
                    })
                },
            )
            fixture.projects.save(changed)
            fixture.gateway.restorePracticeConfiguration(
                project.id, 84, 2, 3, tasks[0].selectedVoicingIds, restoreVoicings = true,
            )
            val restored = fixture.projects.read(project.id)
            assertEquals(84, restored?.bpm)
            assertEquals(2, restored?.transposeSemitones)
            assertEquals(3, restored?.capoFret)
            assertEquals("voicing-old", restored?.sections?.single()?.rows?.single()?.chordEvents?.first()?.selectedVoicingId)

            val progress = fixture.gateway.detail(project.id)?.progress
            assertEquals(60, progress?.totalPracticeSeconds)
            assertEquals(72, progress?.highestCompletedBpm)
            assertEquals(1, progress?.unresolvedDifficultyCount)
            assertEquals(1, progress?.completionCount)
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("song-practice-gateway").toFile()
        try {
            block(Fixture(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    private class Fixture(directory: File) {
        private val repository = ChordRepository()
        val projects = SongProjectStore(SongProjectStore.defaultFile(directory))
        val runs = SongPracticeRunStore(SongPracticeRunStore.defaultFile(directory))
        val difficulties = UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(directory))
        val attempts = TransitionAttemptStore(TransitionAttemptStore.defaultFile(directory))
        private val preferences = PracticePreferencesStore(PracticePreferencesStore.defaultFile(directory))
        private val resolver = RepositorySongChordResolver(repository)
        val gateway = DefaultSongGateway(
            projectStore = projects,
            runStore = runs,
            difficultyStore = difficulties,
            parser = SongSheetParser(resolver),
            importDraftStore = SongImportDraftStore(SongImportDraftStore.defaultFile(directory)),
            arrangementEngine = SongArrangementEngine(repository),
            practicePreferencesStore = preferences,
            transitionAttemptStore = attempts,
            progressionGateway = FakeProgressionGateway(),
            transitionExtractor = SongTransitionExtractor(resolver),
            ioDispatcher = Dispatchers.Unconfined,
            clock = { 1_000L },
        )

        fun project(timed: Boolean, repeat: Int): SongProject {
            val events = listOf(
                SongChordEvent("event-c", "C", "C", null, 2.0.takeIf { timed }, null, 0, 0),
                SongChordEvent("event-g", "G", "G", null, 4.0.takeIf { timed }, null, 1, 1),
            )
            return SongProject(
                id = "song",
                title = "练习曲",
                artist = "",
                originalText = "| C G |",
                originalKey = "C",
                transposeSemitones = 0,
                capoFret = 0,
                bpm = 60,
                timeSignature = "4/4",
                timingState = if (timed) SongTimingState.EXPLICIT_BEATS else SongTimingState.UNTYPED,
                sections = listOf(
                    SongSection(
                        "section", "主歌", SongSectionType.VERSE, 0, repeat,
                        listOf(SongRow("row", "歌词", "C G", events, 0)),
                    ),
                ),
                notes = "",
                createdAt = 10L,
                updatedAt = 10L,
            )
        }
    }

    private class FakeProgressionGateway : ProgressionGateway {
        override suspend fun createDraft(seed: String, name: String): ProgressionUiModel {
            val steps = seed.split(Regex("\\s+")).filter { it.isNotBlank() }.mapIndexed { index, symbol ->
                ProgressionStepUi(symbol, "", 4.0, "", index, null, emptyList())
            }
            return ProgressionUiModel(
                "draft", name, "C", "4/4", 60, true, steps, 0L, 0L, "", false,
                playbackMode = ProgressionPlaybackMode.WHOLE_CHORD,
            )
        }

        override suspend fun list(): List<ProgressionSummaryUi> = emptyList()
        override suspend fun presets(keySignature: String): List<ProgressionPresetUi> = emptyList()
        override suspend fun createPresetDraft(presetId: String, keySignature: String) = error("unused")
        override suspend fun loadEditor(id: String): ProgressionUiModel? = null
        override suspend fun saveDraft(value: ProgressionUiModel) = Unit
        override suspend fun save(value: ProgressionUiModel) = value
        override suspend fun appendSymbols(value: ProgressionUiModel, symbols: String) = value
        override suspend fun recommend(value: ProgressionUiModel) = value
        override suspend fun duplicate(id: String, name: String) = error("unused")
        override suspend fun rename(id: String, name: String) = error("unused")
        override suspend fun delete(id: String): ProgressionUiModel? = null
        override suspend fun restore(value: ProgressionUiModel) = error("unused")
    }
}
