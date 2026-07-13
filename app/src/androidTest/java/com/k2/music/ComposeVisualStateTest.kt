package com.k2.music

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.k2.music.ui.gateway.LibraryFilter
import com.k2.music.ui.library.LibraryScreen
import com.k2.music.ui.library.LibraryUiState
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.theme.MusicTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

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
