package com.stillune.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val BrandGray = Color(0xFFE8E8E8)
internal val BrandPink = Color(0xFFF779B4)
internal val InkAccent = Color(0xFF122B62)
internal val InkBlue = Color(0xFF3D5384)
internal val InkMuted = Color(0xFF535A6B)
internal val AppBackground = Color(0xFFF3F3F5)
internal val AppSurface = Color(0xFFFCFCFE)
internal val SubtleSurface = Color(0xFFEAEAF0)
internal val SelectedSurface = Color(0xFFFBE3EF)
internal val PanelBorder = Color(0xFFD8D9E2)
private val Ink = Color(0xFF1C2335)

internal object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val page = 24.dp
    val xxl = 28.dp
    val section = 32.dp
    val touch = 48.dp
}

internal object Corners {
    val smallRadius = 4.dp
    val innerRadius = 8.dp
    val controlRadius = 12.dp
    val panelRadius = 20.dp
    val sheetRadius = 28.dp

    val Small = SquircleShape(smallRadius)
    val Inner = SquircleShape(innerRadius)
    val Control = SquircleShape(controlRadius)
    val Panel = SquircleShape(panelRadius)
    val InsetPanel = SquircleShape(panelRadius - Space.s)
    val InsetRow = SquircleShape(controlRadius - Space.xs)
    val Sheet = RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius)
}

private val StilluneColors = lightColorScheme(
    primary = InkAccent, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FA), onPrimaryContainer = InkAccent,
    secondary = InkBlue, onSecondary = Color.White,
    secondaryContainer = SelectedSurface, onSecondaryContainer = InkAccent,
    tertiary = Color(0xFF872B59), onTertiary = Color.White,
    tertiaryContainer = SelectedSurface, onTertiaryContainer = Color(0xFF501437),
    background = AppBackground, onBackground = Ink,
    surface = AppSurface, onSurface = Ink,
    surfaceVariant = SubtleSurface, onSurfaceVariant = InkMuted,
    surfaceTint = InkAccent, outline = Color(0xFF747988), outlineVariant = PanelBorder,
    surfaceBright = Color.White, surfaceDim = BrandGray,
    surfaceContainerLowest = Color.White, surfaceContainerLow = AppSurface,
    surfaceContainer = AppBackground, surfaceContainerHigh = SubtleSurface,
    surfaceContainerHighest = PanelBorder,
    error = Color(0xFF9C3F3D), onError = Color.White,
    errorContainer = Color(0xFFFFE4E1), onErrorContainer = Color(0xFF601E1D),
    inverseSurface = InkAccent, inverseOnSurface = Color.White,
    inversePrimary = Color(0xFFB9CAFC),
)

private val StilluneShapes = Shapes(
    extraSmall = RoundedCornerShape(Corners.smallRadius),
    small = RoundedCornerShape(Corners.innerRadius),
    medium = RoundedCornerShape(Corners.controlRadius),
    large = RoundedCornerShape(Corners.panelRadius),
    extraLarge = RoundedCornerShape(Corners.sheetRadius),
)

@Composable
fun StilluneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StilluneColors, shapes = StilluneShapes, content = content)
}
