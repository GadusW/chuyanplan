package com.stillune.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AudioFormatBadge(format: String, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val label = format.ifBlank { "未知格式" }.uppercase()
    val icon = when (label) {
        "MP3" -> Icons.Rounded.MusicNote
        "FLAC" -> Icons.Rounded.GraphicEq
        "WAV" -> Icons.Rounded.Waves
        "WAVE" -> Icons.Rounded.Water
        "AAC" -> Icons.Rounded.Bolt
        "ALAC" -> Icons.Rounded.Album
        "M4A" -> Icons.Rounded.Headphones
        "MP4" -> Icons.Rounded.PlayCircleOutline
        "AIFF" -> Icons.Rounded.Piano
        "AIF" -> Icons.Rounded.KeyboardVoice
        "OGG" -> Icons.Rounded.BubbleChart
        "OPUS" -> Icons.Rounded.SurroundSound
        "WMA" -> Icons.Rounded.Window
        "APE" -> Icons.Rounded.FilterHdr
        "WV" -> Icons.Rounded.StackedLineChart
        "PCM" -> Icons.Rounded.MultilineChart
        else -> Icons.Rounded.AudioFile
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(if (showLabel) 22.dp else 34.dp).clip(SquircleShape(7.dp))
            .background(InkBlue.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(if (showLabel) 14.dp else 20.dp), tint = InkBlue)
        }
        if (showLabel) Text(label, color = InkBlue, fontSize = 11.sp)
    }
}
