package com.stillune.app.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.stillune.app.R
import com.stillune.app.immersion.ImmersionDynamics
import com.stillune.app.immersion.Tilt
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Repositions the cover's texture coordinates through a bounded, softly lit water surface. */
internal class CoverRefraction(private val artwork: Bitmap) {
    private class Wave {
        var x = 0f
        var y = 0f
        var radius = 0f
        var width = 0f
        var amplitude = 0f

        fun set(x: Float, y: Float, radius: Float, width: Float, amplitude: Float) {
            this.x = x
            this.y = y
            this.radius = radius
            this.width = width
            this.amplitude = amplitude
        }
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private val waves = Array(6) { Wave() }
    private val vertices = FloatArray(57 * 57 * 2)
    private val highlights = IntArray(57 * 57)
    private val white = Bitmap.createBitmap(intArrayOf(-1), 1, 1, Bitmap.Config.ARGB_8888)

    fun draw(
        canvas: Canvas, width: Float, height: Float, tilt: Tilt, level: Float,
        ripples: List<ImmersionDynamics.Ripple>, fullDetail: Boolean
    ) {
        val unit = minOf(width, height)
        if (unit <= 0f) return
        // Leave a small sampling margin so even overlapping ripples cover the screen edges.
        val margin = unit * .045f
        val scale = maxOf((width + 2f * margin) / artwork.width, (height + 2f * margin) / artwork.height)
        val coverWidth = artwork.width * scale
        val coverHeight = artwork.height * scale
        val left = (width - coverWidth) * .5f
        val top = (height - coverHeight) * .5f
        bounds.set(left, top, left + coverWidth, top + coverHeight)

        var count = 0
        val surface = level * .008f + tilt.motion * .011f
        if (surface > .00025f) {
            waves[count++].set(tilt.x * .19f, tilt.y * .16f, .24f + level * .05f, .20f, surface)
        }
        val limit = if (fullDetail) 5 else 2
        for (index in maxOf(0, ripples.size - limit) until ripples.size) {
            val ripple = ripples[index]
            val age = ripple.age
            val fade = (1f - exp(-age * 17f)) * exp(-age * 1.9f) * (1f - age / ripple.life)
            waves[count++].set(
                ripple.x + tilt.x * age * .09f, ripple.y + tilt.y * age * .08f,
                .065f + age * (.41f + ripple.strength * .17f),
                .12f + age * .076f, ripple.strength * fade * .067f
            )
        }
        if (count == 0) {
            canvas.drawBitmap(artwork, null, bounds, paint)
            return
        }

        val cells = if (fullDetail) 56 else 36
        val centerX = width * .5f
        val centerY = height * .5f
        val twist = tilt.x * .23f - tilt.y * .18f
        val squeeze = 1f + tilt.y * .13f
        var vertex = 0
        for (row in 0..cells) {
            val py = top + coverHeight * row / cells
            val y = (py - centerY) / unit
            for (column in 0..cells) {
                val px = left + coverWidth * column / cells
                val x = (px - centerX) / unit
                var offsetX = 0f
                var offsetY = 0f
                var highlight = 0f
                for (index in 0 until count) {
                    val wave = waves[index]
                    val dx = x - wave.x
                    val dy = (y - wave.y) * squeeze
                    val outer = wave.radius + wave.width
                    if (abs(dx) >= outer || abs(dy) >= outer) continue
                    val distance = sqrt(dx * dx + dy * dy)
                    val phase = (distance - wave.radius) / wave.width
                    if (abs(phase) >= 1f || distance < .0001f) continue
                    val envelope = 1f - phase * phase
                    val crest = sin(phase * 4.7f) * envelope * envelope
                    val displacement = crest * wave.amplitude / distance
                    val curl = .30f * crest + twist
                    offsetX += (dx - dy * curl) * displacement
                    offsetY += (dy + dx * curl) * displacement
                    val light = ((dx - dy) / distance * .36f + .38f).coerceIn(0f, 1f)
                    highlight += maxOf(0f, crest) * wave.amplitude * light * 2.9f
                }
                vertices[vertex * 2] = px + offsetX.coerceIn(-.04f, .04f) * unit
                vertices[vertex * 2 + 1] = py + offsetY.coerceIn(-.04f, .04f) * unit
                highlights[vertex++] = ((highlight.coerceAtMost(.065f) * 255f).toInt() shl 24) or 0x00ffffff
            }
        }
        canvas.drawBitmapMesh(artwork, cells, cells, vertices, 0, null, 0, paint)
        canvas.drawBitmapMesh(white, cells, cells, vertices, 0, highlights, 0, paint)
    }
}

/** Render the built-in artwork in the same full-screen surface for tracks without a cover. */
internal fun defaultRippleArtwork(resources: Resources): Bitmap {
    val side = 512
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    paint.shader = LinearGradient(96f, 0f, 432f, side.toFloat(),
        intArrayOf(0xff142339.toInt(), 0xff476986.toInt(), 0xff1a233b.toInt()), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
    paint.shader = null
    BitmapFactory.decodeResource(resources, R.drawable.stillune_foreground)?.let { mark ->
        canvas.drawBitmap(mark, null, RectF(116f, 116f, 396f, 396f), paint)
    }
    return bitmap
}
