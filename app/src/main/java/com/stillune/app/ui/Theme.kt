package com.stillune.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GlassGround = Color(0xFFEDF4F8)
val GlassSurfaceColor = Color(0xFFF7FAFD)
val InkAccent = Color(0xFF214B68)
val InkBlue = Color(0xFF315F7D)
val InkMuted = Color(0xFF41576C)

@Composable
fun StilluneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = InkAccent, onPrimary = Color.White, primaryContainer = Color(0xFFD8EAF5),
        onPrimaryContainer = Color(0xFF16394F),
        secondary = InkBlue, onSecondary = Color.White,
        background = GlassGround, onBackground = Color(0xFF192E42),
        surface = GlassSurfaceColor, onSurface = Color(0xFF192E42),
        surfaceVariant = Color(0xFFE2EDF4), onSurfaceVariant = InkMuted,
        outline = Color(0xFF617990), error = Color(0xFF9C3F3D),
        inverseSurface = Color(0xFF243F55), inverseOnSurface = Color.White,
        inversePrimary = Color(0xFFBCE2FA),
    ), content = content)
}
