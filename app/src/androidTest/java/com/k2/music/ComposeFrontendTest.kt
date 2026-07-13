package com.k2.music

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeFrontendTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComposeMainActivity>()

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
            composeRule.onAllNodesWithText("筛选（已启用）").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("清除全部").performClick()

        composeRule.onNodeWithText("C", useUnmergedTree = true).performClick()
        waitForTag("chord_detail_screen")
        composeRule.onNodeWithTag("chord_detail_content")
            .performScrollToNode(hasText("2. C 大横按"))
        composeRule.onNodeWithText("2. C 大横按").performScrollTo().performClick()
        composeRule.onNodeWithText("2. C 大横按").assertIsSelected()
    }

    @Test
    fun theoreticalChordWithoutVoicingShowsAnExplicitFallback() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("home_search_launcher").performClick()
        composeRule.onNodeWithTag("home_search_field").performTextInput("CmMaj7")
        composeRule.onNodeWithTag("home_search_field").performImeAction()
        waitForTag("chord_detail_screen")
        composeRule.onNodeWithText(
            "该和弦理论数据可用，当前暂无收录指法。主操作会试听组成音。",
        ).performScrollTo().assertExists()
    }

    @Test
    fun workbenchOpensRecognitionAndTransposeTasks() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        composeRule.onNodeWithTag("tool_recognition").performClick()
        waitForTag("recognition_screen")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForTag("workbench_screen")
        composeRule.onNodeWithTag("tool_transpose").performClick()
        waitForTag("transpose_screen")
    }

    @Test
    fun recognitionReachesHighFretsAndReturnsCandidates() {
        waitForTag("home_content")
        composeRule.onNodeWithTag("nav_workbench", useUnmergedTree = true).performClick()
        waitForTag("workbench_screen")
        composeRule.onNodeWithTag("tool_recognition").performClick()
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
        composeRule.onNodeWithTag("tool_transpose").performClick()
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
        waitForTag("practice_setup_screen")
        composeRule.onNodeWithTag("practice_setup_list")
            .performScrollToNode(hasTestTag("start_practice"))
        composeRule.onNodeWithTag("start_practice").performClick()
        waitForTag("practice_session_screen")
        composeRule.onNodeWithTag("practice_complete_once").performClick()
        composeRule.onNodeWithTag("finish_practice").performClick()
        waitForTag("practice_result_screen")
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
}
