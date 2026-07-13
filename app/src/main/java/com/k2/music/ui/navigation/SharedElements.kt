package com.k2.music.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.k2.music.ui.theme.LocalMusicMotion

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedChordBounds(symbol: String, element: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    if (!LocalMusicMotion.current.allowSpatialTransitions) return this
    return with(sharedScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState("chord:$symbol:$element"),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
