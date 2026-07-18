package com.k2.music.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF),
    onPrimaryContainer = Color(0xFF003B7A),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE9FF),
    onSecondaryContainer = Color(0xFF29266F),
    tertiary = Color(0xFFC15F00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8D1),
    onTertiaryContainer = Color(0xFF5A2B00),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceDim = Color(0xFFD8D8DD),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFEDEDF2),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFF0A84FF),
    surfaceTint = Color(0xFF007AFF),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE2E2E7),
    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E7),
    onErrorContainer = Color(0xFF79000A),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF173D69),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF8E8CFF),
    onSecondary = Color(0xFF17154E),
    secondaryContainer = Color(0xFF39376C),
    onSecondaryContainer = Color(0xFFE4E3FF),
    tertiary = Color(0xFFFF9F0A),
    onTertiary = Color(0xFF492900),
    tertiaryContainer = Color(0xFF5B3500),
    onTertiaryContainer = Color(0xFFFFE2B7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3A3C),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF151517),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFE5E5EA),
    inverseOnSurface = Color(0xFF2C2C2E),
    inversePrimary = Color(0xFF0066CC),
    surfaceTint = Color(0xFF0A84FF),
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF5C1714),
    onErrorContainer = Color(0xFFFFDAD7),
)

@Immutable
data class MusicExtraColors(
    val energy: Color,
    val onEnergy: Color,
    val warmAccent: Color,
    val success: Color,
)

internal val LightExtraColors = MusicExtraColors(
    energy = Color(0xFFE5F0FF),
    onEnergy = Color(0xFF003B7A),
    warmAccent = Color(0xFFFF9F0A),
    success = Color(0xFF248A3D),
)

internal val DarkExtraColors = MusicExtraColors(
    energy = Color(0xFF173D69),
    onEnergy = Color(0xFFD6E8FF),
    warmAccent = Color(0xFFFF9F0A),
    success = Color(0xFF30D158),
)

val LocalMusicExtraColors = staticCompositionLocalOf { LightExtraColors }
