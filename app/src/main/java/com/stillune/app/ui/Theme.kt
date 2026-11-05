package com.stillune.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF202D46)
val Panel = Color(0xFF344661)
val Moon = Color(0xFFFFEDCC)
val Mist = Color(0xFFB7E5FF)
val Quiet = Color(0xFFC2CFE3)

@Composable
fun StilluneTheme(content: @Composable () -> Unit) {
    // Preserve the reviewed brand palette rather than replacing it with wallpaper colors.
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Moon, onPrimary = Night, primaryContainer = Color(0xFF465B79),
        secondary = Mist, onSecondary = Night,
        background = Night, onBackground = Color(0xFFF2F1EF),
        surface = Panel, onSurface = Color(0xFFF2F1EF),
        surfaceVariant = Color(0xFF3E526D), onSurfaceVariant = Quiet,
        outline = Color(0xFF59677D), error = Color(0xFFFFB4AB),
        inverseSurface = Color(0xFF30415B), inverseOnSurface = Moon, inversePrimary = Mist,
    ), content = content)
}
