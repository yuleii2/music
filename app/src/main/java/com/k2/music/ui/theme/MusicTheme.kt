package com.k2.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ThemeMode
import com.k2.music.ui.preferences.LocalExperienceCapabilities
import com.k2.music.ui.preferences.capabilities

@Composable
fun MusicTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val dynamicAccent = when {
        !settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    // Keep the restrained grouped surfaces intact; dynamic color may only replace the accent.
    val colorScheme = dynamicAccent?.let { accent ->
        baseColorScheme.copy(
            primary = accent.primary,
            onPrimary = accent.onPrimary,
            primaryContainer = accent.primaryContainer,
            onPrimaryContainer = accent.onPrimaryContainer,
        )
    } ?: baseColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors
    val motion = motionTokens(settings.motionLevel, systemAnimationsEnabled(context))

    DisposableEffect(darkTheme, colorScheme.background) {
        val activity = context as? Activity
        activity?.window?.let { window ->
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose { }
    }

    CompositionLocalProvider(
        LocalMusicExtraColors provides extraColors,
        LocalMusicMotion provides motion,
        LocalExperienceCapabilities provides settings.experienceMode.capabilities(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MusicTypography,
            shapes = MusicShapes,
            content = content,
        )
    }
}
