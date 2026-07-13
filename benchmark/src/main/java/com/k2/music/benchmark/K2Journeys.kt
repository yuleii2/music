package com.k2.music.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.k2.music"

internal fun MacrobenchmarkScope.launchReady() {
    pressHome()
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.ComposeMainActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        },
    )
    device.waitForResource("home_content", 20_000)
}

internal fun MacrobenchmarkScope.openLibraryAndScroll() {
    device.clickText("和弦")
    val grid = device.waitForResource("library_grid")
    val bounds = grid.visibleBounds
    repeat(3) {
        device.swipe(bounds.centerX(), bounds.bottom - 24, bounds.centerX(), bounds.top + 24, 12)
        device.waitForIdle(750)
    }
    repeat(4) {
        device.swipe(bounds.centerX(), bounds.top + 24, bounds.centerX(), bounds.bottom - 24, 12)
        device.waitForIdle(750)
    }
}

internal fun MacrobenchmarkScope.openChordDetailAndSwitchVoicing() {
    device.waitForResource("library_grid")
    val cCard = device.wait(Until.findObject(By.descStartsWith("C，大三和弦")), 10_000)
        ?: device.wait(Until.findObject(By.text("C")), 10_000)
        ?: error("C chord card was not found")
    device.tap(cCard)
    device.waitForResource("chord_detail_screen")
    device.wait(Until.findObject(By.textStartsWith("2. ")), 5_000)?.let(device::tap)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openProgressionAndPlay() {
    device.pressBack()
    device.clickText("工具")
    device.waitForResource("workbench_screen")
    device.tap(device.scrollUntilText("和弦进行"))
    device.waitForResource("progression_list_screen")
    device.wait(Until.findObject(By.text("预设")), 5_000)?.let(device::tap)
    val usePreset = device.scrollUntilText("使用预设")
    device.tap(usePreset)
    device.waitForResource("progression_editor_screen")
    device.wait(Until.findObject(By.desc("播放")), 10_000)?.let(device::tap)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openPracticeAndInteract() {
    device.pressBack()
    device.pressBack()
    device.clickText("练习")
    device.waitForResource("practice_home_screen")
    device.tapAndAwait("practice_quick_start", "practice_setup_screen")
    device.scrollUntilResource("start_practice")
    device.tapAndAwait("start_practice", "practice_session_screen")
    device.tap(device.waitForResource("practice_complete_once"))
    device.wait(Until.findObject(By.desc("暂停练习")), 5_000)?.let(device::tap)
    device.waitForIdle()
}

private fun UiDevice.waitForResource(tag: String, timeout: Long = 10_000): UiObject2 =
    wait(Until.findObject(By.res(tag)), timeout)
        ?: error("Timed out waiting for resource tag: $tag")

private fun UiDevice.clickText(text: String) {
    val target = wait(Until.findObject(By.text(text)), 10_000)
        ?: error("Timed out waiting for text: $text")
    tap(target)
}

private fun UiDevice.tap(target: UiObject2) {
    val bounds = target.visibleBounds
    check(click(bounds.centerX(), bounds.centerY())) { "Unable to tap ${target.text ?: target.contentDescription}" }
    waitForIdle()
}

private fun UiDevice.tapAndAwait(sourceTag: String, destinationTag: String) {
    repeat(4) {
        wait(Until.findObject(By.res(destinationTag)), 1_000)?.let { return }
        val source = wait(Until.findObject(By.res(sourceTag)), 4_000)
        if (source != null) {
            tap(source)
        }
        // Navigation can remove the source semantics before the destination's first composed frame.
        wait(Until.findObject(By.res(destinationTag)), 12_000)?.let { return }
    }
    error("Timed out navigating from $sourceTag to $destinationTag")
}

private fun UiDevice.scrollUntilText(text: String): UiObject2 {
    wait(Until.findObject(By.text(text)), 1_000)?.let { return it }
    val scrollable = wait(Until.findObject(By.scrollable(true)), 5_000)
        ?: error("No scrollable container found while looking for $text")
    repeat(8) {
        scrollable.scroll(Direction.DOWN, 0.75f)
        waitForIdle()
        wait(Until.findObject(By.text(text)), 1_000)?.let { return it }
    }
    error("Timed out scrolling to text: $text")
}

private fun UiDevice.scrollUntilResource(tag: String): UiObject2 {
    wait(Until.findObject(By.res(tag)), 1_000)?.let { return it }
    val scrollable = wait(Until.findObject(By.scrollable(true)), 5_000)
        ?: error("No scrollable container found while looking for $tag")
    val bounds = scrollable.visibleBounds
    repeat(8) {
        swipe(bounds.centerX(), bounds.bottom - 24, bounds.centerX(), bounds.top + 24, 12)
        waitForIdle(500)
        wait(Until.findObject(By.res(tag)), 1_000)?.let { return it }
    }
    error("Timed out scrolling to resource: $tag")
}
