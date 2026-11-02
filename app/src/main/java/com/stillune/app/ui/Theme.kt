package com.stillune.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF101B30)
val Panel = Color(0xFF1C2B43)
val Moon = Color(0xFFF4E3BE)
val Mist = Color(0xFF9BCBEA)
val Quiet = Color(0xFFAAB8CF)

@Composable
fun StilluneTheme(content: @Composable () -> Unit) {
    // Preserve the reviewed brand palette rather than replacing it with wallpaper colors.
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Moon, onPrimary = Night, primaryContainer = Color(0xFF3B4255),
        secondary = Mist, onSecondary = Night,
        background = Night, onBackground = Color(0xFFF2F1EF),
        surface = Panel, onSurface = Color(0xFFF2F1EF),
        surfaceVariant = Color(0xFF26364F), onSurfaceVariant = Quiet,
        outline = Color(0xFF59677D), error = Color(0xFFFFB4AB),
        inverseSurface = Color(0xFF30415B), inverseOnSurface = Moon, inversePrimary = Mist,
    ), content = content)
}
