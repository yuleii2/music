package com.k2.music.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.k2.music.ui.preferences.MotionLevel

@Immutable
data class MusicMotionTokens(
    val instant: Int,
    val quick: Int,
    val standard: Int,
    val emphasized: Int,
    val complex: Int,
    val allowSpatialTransitions: Boolean,
    val allowStagger: Boolean,
    val allowSprings: Boolean,
)

val LocalMusicMotion = staticCompositionLocalOf {
    motionTokens(MotionLevel.FULL, systemAnimationsEnabled = true)
}

fun motionTokens(level: MotionLevel, systemAnimationsEnabled: Boolean): MusicMotionTokens {
    if (!systemAnimationsEnabled || level == MotionLevel.OFF) {
        return MusicMotionTokens(0, 0, 0, 0, 0, false, false, false)
    }
    if (level == MotionLevel.REDUCED) {
        return MusicMotionTokens(70, 100, 140, 160, 180, false, false, false)
    }
    return MusicMotionTokens(90, 160, 240, 360, 480, true, true, true)
}

fun systemAnimationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f
}.getOrDefault(true)
