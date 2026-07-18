package com.k2.music

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.k2.music.song.SongChordEvent
import com.k2.music.song.SongRow
import com.k2.music.song.SongSection
import com.k2.music.song.SongSectionType
import com.k2.music.song.SongTimingState
import com.k2.music.song.SongTransition
import com.k2.music.song.SongProject
import com.k2.music.ui.gateway.ProgressionPlaybackUiState
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionUiModel
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.preferences.ThemeMode
import com.k2.music.ui.song.SongDetailData
import com.k2.music.ui.song.SongDetailScreen
import com.k2.music.ui.song.SongDetailUiState
import com.k2.music.ui.song.SongEditorScreen
import com.k2.music.ui.song.SongEditorUiState
import com.k2.music.ui.song.SongPracticePreparation
import com.k2.music.ui.song.SongPracticeScreen
import com.k2.music.ui.song.SongPracticeUiState
import com.k2.music.ui.theme.MusicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SongComposeAccessibilityTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComposeMainActivity>()

    @Test
    fun darkThemeTwoHundredPercentPracticeKeepsTalkBackCurrentAndNextChordSemantics() {
        composeRule.activity.setContent {
            MusicTheme(AppSettings(themeMode = ThemeMode.DARK, experienceMode = ExperienceMode.BEGINNER)) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    SongPracticeScreen(
                        state = practiceState(),
                        playback = ProgressionPlaybackUiState(stepIndex = 0, measureNumber = 1, beatNumber = 1),
                        onBack = {}, onStartPause = {}, onPrevious = {}, onNext = {}, onLoop = {}, onBpm = {},
                        onToggleFretboard = {}, onFinish = {}, onCompleted = {}, onDifficulty = {}, onSave = {},
                        onAbandon = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("song_practice_screen").assertExists()
        composeRule.onNodeWithContentDescription("当前段落：主歌").assertExists()
        composeRule.onNodeWithContentDescription("当前和弦：C").assertExists()
        composeRule.onNodeWithContentDescription("下一和弦：G").assertExists()
    }

    @Test
    fun experienceModeChangesSongTheoryAndAdvancedRhythmDefaultsThroughCapabilities() {
        val professional = mutableStateOf(false)
        composeRule.activity.setContent {
            MusicTheme(
                AppSettings(
                    experienceMode = if (professional.value) ExperienceMode.PROFESSIONAL else ExperienceMode.BEGINNER,
                ),
            ) {
                SongDetailScreen(
                    state = SongDetailUiState(loading = false, data = SongDetailData(project(), emptyList(), emptyList())),
                    onBack = {}, onEdit = {}, onGuidedPractice = {}, onPerformance = { _, _ -> }, onDelete = {},
                    onRetry = {}, onTranspose = {}, onCapo = {}, onAccidentalPreference = {},
                    onResetArrangement = {}, onPinVoicing = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("实际听到的调", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        composeRule.runOnIdle { professional.value = true }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("实际听到的调", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.activity.setContent {
            MusicTheme(AppSettings(experienceMode = ExperienceMode.BEGINNER)) {
                SongEditorScreen(
                    state = editorState(),
                    onBack = {}, onTitle = {}, onArtist = {}, onOriginalText = {}, onOriginalKey = {},
                    onBpm = {}, onTimeSignature = {}, onNotes = {}, onReparse = {}, onSectionName = { _, _ -> },
                    onSectionRepeat = { _, _ -> }, onMoveSection = { _, _ -> }, onDeleteSection = {},
                    onDuration = { _, _, _, _ -> }, onVoicing = { _, _, _, _ -> }, onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("song_editor_list").performScrollToNode(hasText("展开高级拍数与固定指法"))
        composeRule.onNodeWithText("展开高级拍数与固定指法").assertExists()

        composeRule.activity.setContent {
            MusicTheme(AppSettings(experienceMode = ExperienceMode.PROFESSIONAL)) {
                SongEditorScreen(
                    state = editorState(),
                    onBack = {}, onTitle = {}, onArtist = {}, onOriginalText = {}, onOriginalKey = {},
                    onBpm = {}, onTimeSignature = {}, onNotes = {}, onReparse = {}, onSectionName = { _, _ -> },
                    onSectionRepeat = { _, _ -> }, onMoveSection = { _, _ -> }, onDeleteSection = {},
                    onDuration = { _, _, _, _ -> }, onVoicing = { _, _, _, _ -> }, onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("song_editor_list").performScrollToNode(hasText("收起高级拍数与固定指法"))
        composeRule.onNodeWithText("收起高级拍数与固定指法").assertExists()
    }

    private fun practiceState(): SongPracticeUiState {
        val progression = ProgressionUiModel(
            id = "song-test",
            name = "测试曲谱",
            keySignature = "C",
            timeSignature = "4/4",
            bpm = 80,
            loop = true,
            steps = listOf(
                ProgressionStepUi("C", "", 4.0, "", 0, null, emptyList()),
                ProgressionStepUi("G", "", 4.0, "", 1, null, emptyList()),
            ),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            notes = "",
            saved = false,
        )
        return SongPracticeUiState(
            loading = false,
            preparation = SongPracticePreparation(
                project = project(),
                sectionId = "section",
                sectionName = "主歌",
                progression = progression,
                transitions = listOf(SongTransition("C", "G")),
                lyricLines = listOf("第一句歌词", "第二句歌词"),
                preciseTiming = true,
                timingMessage = "拍数可靠，可同步当前与下一和弦。",
            ),
            currentBpm = 80,
            showFretboard = false,
        )
    }

    private fun editorState() = SongEditorUiState(
        loading = false,
        project = project(),
        title = "测试曲谱",
        artist = "作者",
        originalText = "[主歌]\n| C | G |",
        originalKey = "C",
        bpmText = "80",
        timeSignature = "4/4",
        notes = "",
        sections = project().sections,
    )

    private fun project(): SongProject {
        val events = listOf(
            SongChordEvent("event-c", "C", "C", 0, 4.0, null, 0, 0),
            SongChordEvent("event-g", "G", "G", 4, 4.0, null, 1, 1),
        )
        return SongProject(
            id = "song",
            title = "测试曲谱",
            artist = "作者",
            originalText = "[主歌]\n| C | G |",
            originalKey = "C",
            transposeSemitones = 0,
            capoFret = 0,
            bpm = 80,
            timeSignature = "4/4",
            timingState = SongTimingState.EXPLICIT_BEATS,
            sections = listOf(
                SongSection(
                    "section", "主歌", SongSectionType.VERSE, 0, 1,
                    listOf(SongRow("row", "歌词", "C G", events, 0)),
                ),
            ),
            notes = "",
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
