package com.stillune.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSeekBar(
    value: Float, durationMs: Long, label: String, onValueChange: (Float) -> Unit, onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1).toFloat()
    val enabled = durationMs > 0
    Slider(value, onValueChange, onValueChangeFinished = onFinish, enabled = enabled, valueRange = 0f..duration,
        modifier = modifier.fillMaxWidth().padding(horizontal = 5.dp).height(48.dp)
            .semantics { contentDescription = label },
        thumb = { Spacer(Modifier.size(0.dp)) },
        track = { slider ->
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val rtl = layoutDirection == LayoutDirection.Rtl
                val start = Offset(if (rtl) size.width else 0f, center.y)
                val end = Offset(if (rtl) 0f else size.width, center.y)
                val dot = start + (end - start) * (slider.value / duration).coerceIn(0f, 1f)
                drawLine(InkMuted.copy(alpha = .74f), start, end, 3.dp.toPx(), StrokeCap.Round)
                if (enabled) {
                    drawLine(InkAccent, start, dot, 3.dp.toPx(), StrokeCap.Round)
                    drawCircle(InkAccent, 5.dp.toPx(), dot)
                }
            }
        })
}
