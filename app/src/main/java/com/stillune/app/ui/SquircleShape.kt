package com.stillune.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Two symmetric cubic segments meet the straight edges with zero curvature. */
internal data class SquircleShape(val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size, with(density) { radius.toPx() }))
}

internal fun squirclePath(size: Size, radius: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    val r = (radius * 1.35f).coerceIn(0f, minOf(w, h) / 2f)
    val a = r * .6f
    val b = r * .4f
    val c = r * .2f
    moveTo(r, 0f)
    lineTo(w - r, 0f)
    cubicTo(w - a, 0f, w - b, 0f, w - c, c)
    cubicTo(w, b, w, a, w, r)
    lineTo(w, h - r)
    cubicTo(w, h - a, w, h - b, w - c, h - c)
    cubicTo(w - b, h, w - a, h, w - r, h)
    lineTo(r, h)
    cubicTo(a, h, b, h, c, h - c)
    cubicTo(0f, h - b, 0f, h - a, 0f, h - r)
    lineTo(0f, r)
    cubicTo(0f, a, 0f, b, c, c)
    cubicTo(b, 0f, a, 0f, r, 0f)
    close()
}
