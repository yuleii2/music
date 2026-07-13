package com.k2.music.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2F6B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F59A),
    onPrimaryContainer = Color(0xFF102015),
    secondary = Color(0xFF53624F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8D0),
    onSecondaryContainer = Color(0xFF152016),
    tertiary = Color(0xFF8B5600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB7),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF171A17),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF171A17),
    surfaceVariant = Color(0xFFECE8DE),
    onSurfaceVariant = Color(0xFF62675F),
    outline = Color(0xFFD7D2C8),
    outlineVariant = Color(0xFFE6E1D7),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FE6B6),
    onPrimary = Color(0xFF0B391F),
    primaryContainer = Color(0xFF214C31),
    onPrimaryContainer = Color(0xFFC3F5CD),
    secondary = Color(0xFFB8CBB4),
    onSecondary = Color(0xFF243426),
    secondaryContainer = Color(0xFF354937),
    onSecondaryContainer = Color(0xFFD4E8D0),
    tertiary = Color(0xFFFFC77D),
    onTertiary = Color(0xFF482900),
    tertiaryContainer = Color(0xFF653D00),
    onTertiaryContainer = Color(0xFFFFDDB7),
    background = Color(0xFF0D100E),
    onBackground = Color(0xFFF2F5EF),
    surface = Color(0xFF171B18),
    onSurface = Color(0xFFF2F5EF),
    surfaceVariant = Color(0xFF232922),
    onSurfaceVariant = Color(0xFFB8C0B5),
    outline = Color(0xFF3E463E),
    outlineVariant = Color(0xFF2D342D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Immutable
data class MusicExtraColors(
    val energy: Color,
    val onEnergy: Color,
    val warmAccent: Color,
    val success: Color,
)

internal val LightExtraColors = MusicExtraColors(
    energy = Color(0xFFB9F27C),
    onEnergy = Color(0xFF172800),
    warmAccent = Color(0xFFFFB65C),
    success = Color(0xFF2F6B45),
)

internal val DarkExtraColors = MusicExtraColors(
    energy = Color(0xFFB9F27C),
    onEnergy = Color(0xFF172800),
    warmAccent = Color(0xFFFFC77D),
    success = Color(0xFF9FE6B6),
)

val LocalMusicExtraColors = staticCompositionLocalOf { LightExtraColors }
