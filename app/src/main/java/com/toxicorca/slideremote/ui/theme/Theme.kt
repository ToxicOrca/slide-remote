package com.toxicorca.slideremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0B0E13)
    val Surface = Color(0xFF11161D)
    val SurfaceLow = Color(0xFF0F1318)
    val SurfaceMid = Color(0xFF161C25)
    val SurfaceHigh = Color(0xFF1C2330)
    val SurfaceHighest = Color(0xFF232B3A)
    val Outline = Color(0xFF2E3848)
    val OutlineSoft = Color(0xFF222A36)
    val Text = Color(0xFFE6EAF0)
    val TextDim = Color(0xFF97A1B0)
    val Accent = Color(0xFF4FD1C5)
    val AccentDeep = Color(0xFF0F3B37)
    val Amber = Color(0xFFFFC266)
    val Danger = Color(0xFFFF6B6B)
    val Connected = Color(0xFF4ADE80)
}

private val Colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Palette.AccentDeep,
    onPrimaryContainer = Color(0xFFB8F4EE),
    secondary = Color(0xFF9BB4D6),
    onSecondary = Color(0xFF0E1D2E),
    secondaryContainer = Color(0xFF223147),
    onSecondaryContainer = Color(0xFFD5E3F7),
    tertiary = Palette.Amber,
    onTertiary = Color(0xFF2B1A00),
    tertiaryContainer = Color(0xFF4A3510),
    onTertiaryContainer = Color(0xFFFFE2B8),
    error = Palette.Danger,
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD6D6),
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.SurfaceLow,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceMid,
    surfaceContainerHighest = Palette.SurfaceHigh,
    surfaceBright = Palette.SurfaceHighest,
    surfaceDim = Palette.Bg,
    outline = Palette.Outline,
    outlineVariant = Palette.OutlineSoft,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Bg,
    inversePrimary = Color(0xFF006A62),
    scrim = Color(0xFF000000),
)

private val Type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

/** Dark only, always. */
@Composable
fun SlideRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
