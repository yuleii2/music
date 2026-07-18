package com.k2.music

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.k2.music.ui.gateway.LibraryFilter
import com.k2.music.ui.library.LibraryScreen
import com.k2.music.ui.library.LibraryUiState
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.preferences.ThemeMode
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
    fun completeLibraryKeepsAdvancedChoicesInTheFocusedFilterSheet() {
        val gateway = DefaultChordCatalogGateway(ChordRepository())
        val chords = runBlocking { gateway.allChords() }
        val roots = runBlocking { gateway.roots() }
        val qualities = runBlocking { gateway.qualities() }
        assertTrue(chords.size == 582)
        assertTrue(chords.all { it.voicings.isNotEmpty() })
        assertTrue(roots.size == 12)

        composeRule.activity.setContent {
            MusicTheme(AppSettings(experienceMode = ExperienceMode.PROFESSIONAL)) {
                LibraryScreen(
                    state = LibraryUiState(
                        loading = false,
                        roots = roots,
                        qualities = qualities,
                        chords = chords,
                    ),
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

        composeRule.onNodeWithText("筛选").performClick()
        composeRule.onNodeWithText("三和弦", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("C♯/D♭", useUnmergedTree = true).assertExists()
        assertTrue(
            composeRule.onAllNodesWithText("理论和弦\n暂无收录指法", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("应用").performClick()
        capture("visual-complete-library.png")
    }

    @Test
    fun coreChordFormulaRemainsVisibleAcrossExperienceModes() {
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
        composeRule.onNodeWithText("音程", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { professional.value = true }
        composeRule.onNodeWithText("音程", useUnmergedTree = true).assertExists()
    }

    @Test
    fun complexSlashDetailRendersInLightDarkAndLargeFont() {
        val chord = runBlocking {
            checkNotNull(DefaultChordCatalogGateway(ChordRepository()).find("Cmaj9/E").chord)
        }
        val themeMode = mutableStateOf(ThemeMode.LIGHT)
        val fontScale = mutableStateOf(1f)
        composeRule.activity.setContent {
            MusicTheme(AppSettings(themeMode = themeMode.value, experienceMode = ExperienceMode.PROFESSIONAL)) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                    ChordDetailScreen(
                        state = ChordDetailUiState(loading = false, chord = chord, theoryExpanded = true),
                        onBack = {}, onRetry = {}, onSelectVoicing = {}, onToggleFavorite = {},
                        onToggleFamiliar = {}, onPlay = {}, onToggleTheory = {}, onDeleteCustomVoicing = {},
                        onExportCurrent = {}, onExportAll = {}, onExplainWithAi = {}, onStartPractice = {},
                        onAddProgression = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("和弦主体：Cmaj9 · 最低音：E", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("第一转位斜杠和弦", useUnmergedTree = true).assertExists()
        capture("visual-complex-light.png")
        composeRule.runOnIdle {
            themeMode.value = ThemeMode.DARK
            fontScale.value = 2f
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cmaj9/E", useUnmergedTree = true).assertExists()
        capture("visual-complex-dark-large-font.png")
    }

    @Test
    fun advancedVoicingExplainsIntentionalGuitarOmissions() {
        val chord = runBlocking {
            checkNotNull(DefaultChordCatalogGateway(ChordRepository()).find("C13").chord)
        }
        assertTrue(chord.voicings.isNotEmpty())
        assertTrue(chord.voicings.first().omittedIntervals == listOf("5", "11"))

        composeRule.activity.setContent {
            MusicTheme(AppSettings(experienceMode = ExperienceMode.PROFESSIONAL)) {
                ChordDetailScreen(
                    state = ChordDetailUiState(loading = false, chord = chord),
                    onBack = {}, onRetry = {}, onSelectVoicing = {}, onToggleFavorite = {},
                    onToggleFamiliar = {}, onPlay = {}, onToggleTheory = {}, onDeleteCustomVoicing = {},
                    onExportCurrent = {}, onExportAll = {}, onExplainWithAi = {}, onStartPractice = {},
                    onAddProgression = {},
                )
            }
        }

        composeRule.onNodeWithTag("chord_detail_content")
            .performScrollToNode(hasText("吉他常用省略：5 · 11"))
        composeRule.onNodeWithText("吉他常用省略：5 · 11", useUnmergedTree = true)
            .assertExists()
        capture("visual-advanced-voicing.png")
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
