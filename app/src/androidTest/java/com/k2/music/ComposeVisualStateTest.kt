package com.k2.music

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.k2.music.ui.gateway.LibraryFilter
import com.k2.music.ui.library.LibraryScreen
import com.k2.music.ui.library.LibraryUiState
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.theme.MusicTheme
import com.k2.music.ui.detail.ChordDetailScreen
import com.k2.music.ui.detail.ChordDetailUiState
import com.k2.music.ui.gateway.DefaultChordCatalogGateway
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import kotlinx.coroutines.runBlocking

class ComposeVisualStateTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComposeMainActivity>()

    @Test
    fun loadingEmptyAndErrorStatesRenderAndCapture() {
        val visualState = mutableStateOf(LibraryUiState(loading = true))
        composeRule.activity.setContent {
            MusicTheme(AppSettings()) {
                LibraryScreen(
                    state = visualState.value,
                    onQueryChange = {},
                    onSegmentSelected = {},
                    onFilterApplied = {},
                    onClearFilters = {},
                    onOpenChord = {},
                    onToggleFavorite = {},
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onFavoriteSelection = {},
                    onExportSelection = {},
                    onBrowse = {},
                    onCreateCustom = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_loading").assertExists()
        capture("visual-loading.png")

        composeRule.runOnIdle {
            visualState.value = LibraryUiState(
                loading = false,
                query = "zzzzzz",
                filter = LibraryFilter(),
            )
        }
        composeRule.onNodeWithText("没有匹配结果", useUnmergedTree = true).assertExists()
        capture("visual-empty.png")

        composeRule.runOnIdle {
            visualState.value = LibraryUiState(
                loading = false,
                error = "测试错误：离线数据暂不可用。",
            )
        }
        composeRule.onNodeWithText("暂时无法加载", useUnmergedTree = true).assertExists()
        capture("visual-error.png")
    }

    @Test
    fun statisticsUseOneColumnAtTwoHundredPercentFontScale() {
        composeRule.activity.setContent {
            MusicTheme(AppSettings()) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    AdaptiveStatGrid(
                        listOf(
                            AdaptiveStat("第一项", "1"),
                            AdaptiveStat("第二项", "2"),
                            AdaptiveStat("第三项", "3"),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val first = composeRule.onNodeWithText("第一项").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("第二项").fetchSemanticsNode().boundsInRoot
        val third = composeRule.onNodeWithText("第三项").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(first.left - second.left) < 1f)
        assertTrue(kotlin.math.abs(second.left - third.left) < 1f)
        assertTrue(first.top < second.top && second.top < third.top)
    }

    @Test
    fun experienceModeImmediatelyChangesChordDetailInformation() {
        val chord = runBlocking {
            checkNotNull(DefaultChordCatalogGateway(ChordRepository()).find("C").chord)
        }
        val professional = mutableStateOf(false)
        composeRule.activity.setContent {
            MusicTheme(
                AppSettings(
                    experienceMode = if (professional.value) ExperienceMode.PROFESSIONAL else ExperienceMode.BEGINNER,
                ),
            ) {
                ChordDetailScreen(
                    state = ChordDetailUiState(loading = false, chord = chord),
                    onBack = {}, onRetry = {}, onSelectVoicing = {}, onToggleFavorite = {},
                    onToggleFamiliar = {}, onPlay = {}, onToggleTheory = {}, onDeleteCustomVoicing = {},
                    onExportCurrent = {}, onExportAll = {}, onExplainWithAi = {}, onStartPractice = {},
                    onAddProgression = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("音程", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        composeRule.runOnIdle { professional.value = true }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("音程", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(fileName: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val target = File(composeRule.activity.getExternalFilesDir(null), fileName)
        FileOutputStream(target).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val command = "cp ${target.absolutePath} /sdcard/$fileName"
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).use { it.readBytes() }
    }
}
