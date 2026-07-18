package com.k2.music

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.MediaStore
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.learning.LearningProfileStore
import com.k2.music.ui.learning.LearningGoal
import com.k2.music.song.SongChordEvent
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongPracticeRun
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.SongProject
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongRow
import com.k2.music.song.SongSection
import com.k2.music.song.SongSectionType
import com.k2.music.song.SongTimingState
import com.k2.music.song.UserReportedDifficultyStore

class ComposeFrontendTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComposeMainActivity>()

    @Before
    fun ensureOnboardingIsComplete() {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("home_content").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("跳过，使用默认设置").performClick()
            waitForTag("home_content")
        }
        AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
            .setExperienceMode(ExperienceMode.PROFESSIONAL)
        composeRule.waitForIdle()
    }

    @Test
    fun onboardingCanBeRerunCompletedAndSkippedWithoutClearingPracticeData() {
        composeRule.onNodeWithTag("nav_profile", useUnmergedTree = true).performClick()
        waitForTag("profile_screen")
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasText("重新运行首次引导"))
        composeRule.onNodeWithText("重新运行首次引导").performClick()
        waitForTag("onboarding_screen")
        clickOnboardingText("开始设置")
        clickOnboardingText("已会基础和弦")
        clickOnboardingText("继续")
        clickOnboardingText("学习基础和弦")
        clickOnboardingText("练习歌曲伴奏")
        clickOnboardingText("继续")
        clickOnboardingText("10 分钟")
        clickOnboardingText("专业")
        clickOnboardingText("完成设置")
        waitForTag("home_content")

        composeRule.onNodeWithTag("nav_profile", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasText("重新运行首次引导"))
        composeRule.onNodeWithText("重新运行首次引导").performClick()
        waitForTag("onboarding_screen")
        clickOnboardingText("跳过，使用默认设置")
        waitForTag("home_content")
    }

    @Test
    fun fivePrimaryDestinationsAreReachable() {
        waitForTag("home_content")
        listOf("home", "library", "workbench", "practice", "profile").forEach { route ->
            composeRule.onNodeWithTag("nav_$route", useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        waitForTag("library_screen")
    }

    @Test
    fun practiceOpensSongLibraryAndImportPreviewWithoutAddingAnotherBottomTab() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_practice", useUnmergedTree = true).performClick()
        waitForTag("practice_home_screen")
        composeRule.onNodeWithTag("practice_home_screen").performScrollToNode(hasTestTag("song_library_entry"))
        composeRule.onNodeWithTag("song_library_entry").performClick()
        waitForTag("song_library_screen")
        composeRule.onNodeWithText("粘贴曲谱").performClick()
        waitForTag("song_import_screen")
        composeRule.onNodeWithTag("song_import_text").performTextInput("曲名：导航测试曲\n[主歌]\n| C | G |")
        composeRule.onNodeWithTag("song_parse_button").performClick()
        waitForTag("song_import_preview_screen")
        composeRule.onNodeWithText("导航测试曲").assertExists()
        composeRule.onNodeWithTag("song_import_preview_list").performScrollToNode(hasTestTag("song_line_correction_toggle"))
        composeRule.onNodeWithTag("song_line_correction_toggle").performClick()
        composeRule.onNodeWithTag("song_import_preview_list").performScrollToNode(hasText("第 1 行"))
        assertTrue(composeRule.onAllNodesWithText("段落标题").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForTag("song_import_screen")
        composeRule.onNodeWithTag("nav_home", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun songEditPerformanceDifficultyAndGuidedPracticeUseTheRealLocalStores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectStore = SongProjectStore(SongProjectStore.defaultFile(context.filesDir))
        val runStore = SongPracticeRunStore(SongPracticeRunStore.defaultFile(context.filesDir))
        val difficultyStore = UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(context.filesDir))
        val id = "ui-song-${System.currentTimeMillis()}"
        val project = instrumentationSong(id, "UI 曲谱闭环")
        projectStore.save(project)
        try {
            composeRule.onNodeWithTag("nav_practice", useUnmergedTree = true).performClick()
            waitForTag("practice_home_screen")
            composeRule.onNodeWithTag("practice_home_screen").performScrollToNode(hasTestTag("song_library_entry"))
            composeRule.onNodeWithTag("song_library_entry").performClick()
            waitForTag("song_library_screen")
            composeRule.onNodeWithText(project.title).performClick()
            waitForTag("song_detail_screen")

            composeRule.onNodeWithContentDescription("编辑曲谱").performClick()
            waitForTag("song_editor_screen")
            composeRule.onNodeWithTag("song_editor_bpm").performTextClearance()
            composeRule.onNodeWithTag("song_editor_bpm").performTextInput("84")
            composeRule.onNodeWithContentDescription("保存曲谱").performClick()
            composeRule.onNodeWithText("确认保存").performClick()
            waitForTag("song_detail_screen")
            assertEquals(84, projectStore.read(id)?.bpm)

            composeRule.onNodeWithTag("song_detail_list").performScrollToNode(hasTestTag("song_capo_up"))
            composeRule.onNodeWithTag("song_capo_up").performClick()
            composeRule.waitUntil(10_000) { projectStore.read(id)?.capoFret == 1 }

            composeRule.onNodeWithTag("song_detail_list").performScrollToNode(hasTestTag("song_performance_whole"))
            composeRule.onNodeWithTag("song_performance_whole").performClick()
            waitForTag("song_practice_screen")
            composeRule.onNodeWithTag("song_current_chord").assertExists()
            composeRule.onNodeWithTag("song_next_chord").assertExists()
            composeRule.onNodeWithContentDescription("开始或继续连续演奏").performClick()
            composeRule.onNodeWithContentDescription("结束连续演奏").performClick()
            composeRule.onNodeWithText("演奏总结").assertExists()
            composeRule.onNodeWithTag("song_completed_checkbox").performClick()
            composeRule.onNodeWithTag("song_difficulty_checkbox_0").performClick()
            composeRule.onNodeWithTag("save_song_performance").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("连续演奏已保存").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, runStore.forSong(id).size)
            assertEquals(1, difficultyStore.forSong(id).size)

            composeRule.onNodeWithText("返回曲谱详情").performClick()
            waitForTag("song_detail_screen")
            composeRule.onNodeWithTag("song_detail_list").performScrollToNode(hasTestTag("song_guided_whole"))
            composeRule.onNodeWithTag("song_guided_whole").performClick()
            composeRule.onNodeWithTag("song_guided_pair_0").performClick()
            waitForTag("practice_session_screen")
        } finally {
            runStore.deleteForSong(id)
            difficultyStore.deleteForSong(id)
            projectStore.delete(id)
        }
    }

    @Test
    fun songAccompanimentGoalShowsAResumableHomeTaskWithSavedConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectStore = SongProjectStore(SongProjectStore.defaultFile(context.filesDir))
        val runStore = SongPracticeRunStore(SongPracticeRunStore.defaultFile(context.filesDir))
        val difficultyStore = UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(context.filesDir))
        val id = "ui-home-song-${System.currentTimeMillis()}"
        val project = instrumentationSong(id, "首页继续曲")
        projectStore.save(project)
        runStore.save(
            SongPracticeRun(
                "ui-home-run-$id", id, project.sections.single().id, SongPracticeMode.PERFORMANCE,
                72, 0, 0, 1L, 61L, 60, false, emptyList(), loopEnabled = false, showFretboard = false,
            ),
        )
        val learning = (composeRule.activity.application as MusicApplication)
            .appContainer.learningProfileStore
        val previous = learning.profile.value
        learning.save(previous.copy(onboardingCompleted = true, goals = setOf(LearningGoal.SONG_ACCOMPANIMENT)))
        try {
            composeRule.activityRule.scenario.recreate()
            waitForTag("home_content")
            composeRule.onNodeWithTag("home_content").performScrollToNode(hasText("继续连续演奏"))
            composeRule.onNodeWithText("继续连续演奏").performClick()
            waitForTag("song_practice_screen")
            composeRule.onNodeWithText("72 BPM").assertExists()
            composeRule.onNodeWithText("显示指板图").assertExists()
        } finally {
            learning.save(previous)
            runStore.deleteForSong(id)
            difficultyStore.deleteForSong(id)
            projectStore.delete(id)
        }
    }

    @Test
    fun slashChordSearchNavigatesToDetailWithoutBreakingTheRoute() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("home_search_launcher").performClick()
        composeRule.onNodeWithTag("home_search_field").performTextInput("G/B")
        composeRule.onNodeWithTag("home_search_field").performImeAction()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("chord_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("G/B", useUnmergedTree = true).assertExists()
    }

    @Test
    fun detailSurvivesActivityRecreationAndRestoresTheHomeDraft() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("home_search_launcher").performClick()
        composeRule.onNodeWithTag("home_search_field").performTextInput("Cmaj7")
        composeRule.onNodeWithTag("home_search_field").performImeAction()
        waitForTag("chord_detail_screen")

        composeRule.activityRule.scenario.recreate()
        waitForTag("chord_detail_screen")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForTag("home_search_field")
        composeRule.onNodeWithTag("home_search_field").assertTextContains("Cmaj7")
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun favoriteChangeCanBeUndoneFromTheSnackbar() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        waitForTag("library_screen")
        waitForTag("library_grid")
        composeRule.onNodeWithTag("library_grid")
            .performScrollToNode(hasTestTag("library_chord_C"))

        val initiallyFavorite = composeRule
            .onAllNodesWithContentDescription("取消收藏 C", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        val initialAction = if (initiallyFavorite) "取消收藏 C" else "收藏 C"
        composeRule.onNodeWithContentDescription(initialAction, useUnmergedTree = true).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("撤销").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription(initialAction, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun libraryFilterAndDetailVoicingRemainInteractive() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        waitForTag("library_screen")
        composeRule.onNodeWithText("筛选").performClick()
        composeRule.onNodeWithText("仅开放按法").performClick()
        composeRule.onNodeWithText("应用").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("筛选 · 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("清除全部").performClick()

        composeRule.onNodeWithTag("library_grid")
            .performScrollToNode(hasTestTag("library_chord_C"))
        composeRule.onNodeWithTag("library_chord_C", useUnmergedTree = true).performClick()
        waitForTag("chord_detail_screen")
        composeRule.onNodeWithTag("chord_detail_content")
            .performScrollToNode(hasTestTag("voicing_selector"))
        composeRule.onNodeWithTag("voicing_option_0", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("voicing_option_0", useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun advancedChordShowsAValidatedGeneratedVoicing() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("home_search_launcher").performClick()
        composeRule.onNodeWithTag("home_search_field").performTextInput("CmMaj7")
        composeRule.onNodeWithTag("home_search_field").performImeAction()
        waitForTag("chord_detail_screen")
        composeRule.onNodeWithTag("chord_detail_content")
            .performScrollToNode(hasText("1. CmMaj7 可移动按法"))
        composeRule.onNodeWithText("1. CmMaj7 可移动按法").assertExists()
        assertTrue(
            composeRule.onAllNodesWithText(
                "该和弦理论数据可用，当前暂无收录指法。主操作会试听组成音。",
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun workbenchOpensRecognitionAndTransposeTasks() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        clickWorkbenchTool("tool_recognition")
        waitForTag("recognition_screen")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForTag("workbench_screen")
        clickWorkbenchTool("tool_transpose")
        waitForTag("transpose_screen")
    }

    @Test
    fun recognitionReachesHighFretsAndReturnsCandidates() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        clickWorkbenchTool("tool_recognition")
        waitForTag("recognition_screen")
        repeat(7) {
            composeRule.onNodeWithContentDescription("显示更高的五个品位").performClick()
        }
        composeRule.onNodeWithText("显示 8–12 品").assertExists()
        composeRule.onNodeWithText("音符输入").performClick()
        composeRule.onNodeWithTag("recognition_note_input").performTextInput("C E G")
        composeRule.onNodeWithText("识别和弦").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("候选结果").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("候选结果").performScrollTo().assertExists()
        composeRule.onNodeWithText("C", useUnmergedTree = true).assertExists()
    }

    @Test
    fun invalidTransposeClearsThePreviousActionableResult() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        clickWorkbenchTool("tool_transpose")
        waitForTag("transpose_screen")

        composeRule.onNodeWithTag("transpose_input").performTextInput("C G")
        composeRule.onNodeWithTag("transpose_calculate").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("移调结果").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("transpose_input").performTextClearance()
        composeRule.onNodeWithTag("transpose_input").performTextInput("H")
        composeRule.onNodeWithTag("transpose_calculate").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("移调结果").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("移调结果").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("错误", useUnmergedTree = true).assertExists()
    }

    @Test
    fun progressionEditorAddsMultipleChordsAndKeepsAccessibleReorderActions() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        composeRule.onNodeWithTag("workbench_screen")
            .performScrollToNode(hasTestTag("tool_progressions"))
        composeRule.onNodeWithTag("tool_progressions").performClick()
        waitForTag("progression_list_screen")
        composeRule.onNodeWithTag("new_progression").performClick()
        waitForTag("progression_editor_screen")
        composeRule.onNodeWithTag("progression_add_input").performTextInput("C G/B Am F")
        composeRule.onNodeWithContentDescription("添加到进行").performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("左移 F", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithContentDescription("左移 F", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("progression_timeline")
            .performScrollToNode(hasContentDescription("第 2 步，G/B，4 拍"))
        composeRule.onNodeWithContentDescription("第 2 步，G/B，4 拍", useUnmergedTree = true).assertExists()
    }

    @Test
    fun practiceCanStartCompleteAndReachAVisibleResult() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_practice", useUnmergedTree = true).performClick()
        waitForTag("practice_home_screen")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("practice_quick_start").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("practice_quick_start").performClick()
        waitForTag("practice_session_screen")
        composeRule.onNodeWithTag("practice_success").performScrollTo().assertTextContains("跟上了").performClick()
        waitForEnabledTag("practice_failure")
        composeRule.onNodeWithTag("practice_failure").performScrollTo().assertTextContains("没跟上").performClick()
        waitForEnabledTag("finish_practice")
        composeRule.onNodeWithTag("finish_practice").performScrollTo().performClick()
        waitForTag("practice_result_screen")
        composeRule.onNodeWithText("成功率").assertExists()
        composeRule.onNodeWithText("50%").assertExists()
    }

    @Test
    fun homeContinueLastStartsSessionWithoutOpeningSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val records = PracticeRecordStore(PracticeRecordStore.defaultFile(context.filesDir))
        val id = "device-home-continue-${System.currentTimeMillis()}"
        records.save(
            PracticeSession.recorded(
                id, System.currentTimeMillis() - 60_000L, System.currentTimeMillis(),
                PracticeSession.Type.TWO_CHORD_TRANSITION, listOf("C", "G"), 55, "4/4",
                PracticeSession.SwitchMode.EACH_MEASURE, 60, 60, 2, 1, 1, 1,
            ),
        )
        try {
            composeRule.activityRule.scenario.recreate()
            waitForTag("home_content")
            composeRule.onNodeWithTag("home_content").performScrollToNode(hasText("直接继续"))
            composeRule.onNodeWithText("直接继续").performClick()
            waitForTag("practice_session_screen")
            composeRule.onNodeWithTag("practice_setup_screen").assertDoesNotExist()
        } finally {
            records.delete(id)
        }
    }

    @Test
    fun practiceSessionSurvivesActivityRecreationWithoutDoubleCounting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val records = PracticeRecordStore(PracticeRecordStore.defaultFile(context.filesDir))
        val attempts = TransitionAttemptStore(TransitionAttemptStore.defaultFile(context.filesDir))
        val existingSessions = records.list().map { it.id }.toSet()
        val existingAttemptSessions = attempts.list().map { it.sessionId }.toSet()
        try {
            composeRule.onNodeWithTag("nav_practice", useUnmergedTree = true).performClick()
            waitForTag("practice_home_screen")
            composeRule.onNodeWithTag("practice_home_screen").performScrollToNode(hasTestTag("practice_quick_start"))
            composeRule.onNodeWithTag("practice_quick_start").performClick()
            waitForTag("practice_session_screen")
            composeRule.onNodeWithTag("practice_success").performScrollTo().performClick()
            waitForEnabledTag("practice_failure")
            composeRule.activityRule.scenario.recreate()
            waitForTag("practice_session_screen")
            composeRule.onNodeWithContentDescription("尝试 1，成功 1，失败 0").assertExists()
            composeRule.onNodeWithContentDescription("继续练习").performClick()
            waitForEnabledTag("practice_failure")
            composeRule.onNodeWithTag("practice_failure").performScrollTo().performClick()
            waitForEnabledTag("finish_practice")
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithContentDescription("尝试 2，成功 1，失败 1")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("finish_practice").performScrollTo().performClick()
            waitForTag("practice_result_screen")
            val resultDescription = composeRule.onNodeWithTag("practice_result_stats")
                .fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
            assertEquals(listOf("总尝试 2，成功 1，失败 1，最佳连续 1"), resultDescription)
        } finally {
            records.list().filter { it.id !in existingSessions }.forEach { records.delete(it.id) }
            attempts.list().map { it.sessionId }.filter { it !in existingAttemptSessions }.distinct()
                .forEach(attempts::deleteSession)
        }
    }

    @Test
    fun practiceControlsRemainReachableInLandscape() {
        try {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            composeRule.waitUntil(15_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            waitForTag("home_content")
            composeRule.onNodeWithTag("nav_practice", useUnmergedTree = true).performClick()
            waitForTag("practice_home_screen")
            composeRule.onNodeWithTag("practice_home_screen").performScrollToNode(hasTestTag("practice_quick_start"))
            composeRule.onNodeWithTag("practice_quick_start").performClick()
            waitForTag("practice_session_screen")
            composeRule.onNodeWithTag("practice_success").assertTextContains("跟上了")
            composeRule.onNodeWithTag("practice_failure").assertTextContains("没跟上")
        } finally {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun fullBackupPreviewAndRestoreConfirmationUseARealDocumentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "k2-device-ui-${System.currentTimeMillis()}.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri, "w")!!.use { fullBackupManager().writeBackup(it, 10L) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = Instrumentation.ActivityMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addDataType("*/*") },
            Instrumentation.ActivityResult(
                Activity.RESULT_OK,
                Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            ),
            true,
        )
        instrumentation.addMonitor(monitor)
        try {
            composeRule.onNodeWithTag("nav_profile", useUnmergedTree = true).performClick()
            waitForTag("profile_screen")
            composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasText("数据与备份"))
            composeRule.onNodeWithText("数据与备份").performClick()
            waitForTag("data_backup_screen")
            composeRule.onNodeWithText("从备份恢复").performClick()
            composeRule.waitUntil(10_000) { monitor.hits > 0 }
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("恢复预览").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("data_backup_list").performScrollToNode(hasText("确认恢复"))
            composeRule.onNodeWithText("确认恢复").performClick()
            composeRule.onNodeWithText("继续恢复").performClick()
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("恢复结果").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun aiDisabledStateAndContextualExportRemainExplicit() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        composeRule.onNodeWithTag("workbench_screen")
            .performScrollToNode(hasTestTag("tool_ai"))
        composeRule.onNodeWithTag("tool_ai").performClick()
        waitForTag("ai_assistant_screen")
        composeRule.onNodeWithText("AI 尚未配置").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithTag("nav_profile", useUnmergedTree = true).performClick()
        waitForTag("profile_screen")
        composeRule.onNodeWithTag("profile_screen")
            .performScrollToNode(hasText("导出收藏指法"))
        composeRule.onNodeWithText("导出收藏指法").performClick()
        waitForTag("export_screen")
        composeRule.onNodeWithText("选择文件夹").assertExists()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = Instrumentation.ActivityMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
            true,
        )
        instrumentation.addMonitor(monitor)
        try {
            composeRule.onNodeWithText("选择文件夹").performClick()
            composeRule.waitUntil(10_000) { monitor.hits > 0 }
            assertTrue(monitor.hits > 0)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasTestTag(tag)).assertExists()
    }

    private fun instrumentationSong(id: String, title: String): SongProject {
        val events = listOf(
            SongChordEvent("event-c-$id", "C", "C", 0, 4.0, null, 0, 0),
            SongChordEvent("event-g-$id", "G", "G", 4, 4.0, null, 1, 1),
        )
        return SongProject(
            id = id,
            title = title,
            artist = "设备测试",
            originalText = "[主歌]\n| C | G |",
            originalKey = "C",
            transposeSemitones = 0,
            capoFret = 0,
            bpm = 80,
            timeSignature = "4/4",
            timingState = SongTimingState.EXPLICIT_BEATS,
            sections = listOf(
                SongSection(
                    "section-$id", "主歌", SongSectionType.VERSE, 0, 1,
                    listOf(SongRow("row-$id", "测试歌词", "C G", events, 0)),
                ),
            ),
            notes = "",
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    private fun fullBackupManager(): FullBackupManager {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ChordRepository()
        return FullBackupManager(
            AppPreferences(context),
            LearningProfileStore(context),
            UserChordStore(context, repository),
            CustomVoicingStore(context),
            ProgressionStore(ProgressionStore.defaultFile(context.filesDir)),
            ProgressionStore(java.io.File(context.filesDir, "progression-drafts-v1.bin")),
            PracticePreferencesStore(PracticePreferencesStore.defaultFile(context.filesDir)),
            PracticeRecordStore(PracticeRecordStore.defaultFile(context.filesDir)),
            TransitionAttemptStore(TransitionAttemptStore.defaultFile(context.filesDir)),
            AiSettingsStore(context),
            SongProjectStore(SongProjectStore.defaultFile(context.filesDir)),
            SongPracticeRunStore(SongPracticeRunStore.defaultFile(context.filesDir)),
            UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(context.filesDir)),
        )
    }

    private fun waitForEnabledTag(tag: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
    }

    private fun clickWorkbenchTool(tag: String) {
        composeRule.onNodeWithTag("workbench_screen").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun clickOnboardingText(text: String) {
        composeRule.onNodeWithTag("onboarding_screen").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).performClick()
    }
}
