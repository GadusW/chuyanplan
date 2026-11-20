package com.stillune.app.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.stillune.app.R
import com.stillune.app.immersion.ImmersionDynamics
import com.stillune.app.immersion.Tilt
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Folds the complete cover and its soft backdrop into one continuous surface for refraction. */
internal class CoverRefraction(artwork: Bitmap, viewportWidth: Int, viewportHeight: Int) {
    private class Wave {
        var x = 0f
        var y = 0f
        var radius = 0f
        var width = 0f
        var amplitude = 0f
        var lighting = 0f

        fun set(x: Float, y: Float, radius: Float, width: Float, amplitude: Float, lighting: Float) {
            this.x = x
            this.y = y
            this.radius = radius
            this.width = width
            this.amplitude = amplitude
            this.lighting = lighting
        }
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private val waves = Array(6) { Wave() }
    private val vertices = FloatArray(53 * 85 * 2)
    private val shading = IntArray(53 * 85)
    private val white = Bitmap.createBitmap(intArrayOf(Color.WHITE), 1, 1, Bitmap.Config.ARGB_8888)
    private val coverBounds = RectF()
    private val feather: Float
    private val texture: Bitmap

    init {
        // Rebuild only for a different cover or viewport, at a bounded texture resolution.
        val scale = minOf(1f, 1600f / maxOf(viewportWidth, viewportHeight))
        val width = (viewportWidth * scale).roundToInt().coerceAtLeast(1).toFloat()
        val height = (viewportHeight * scale).roundToInt().coerceAtLeast(1).toFloat()
        texture = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(texture)
        val backdrop = blurArtwork(artwork)
        canvas.drawBitmap(backdrop, null, RectF(0f, 0f, width, height), paint)
        canvas.drawColor(0x190D1725)

        val fit = minOf(width * .86f / artwork.width, height * .69f / artwork.height)
        val halfWidth = artwork.width * fit * .5f
        val halfHeight = artwork.height * fit * .5f
        val centerX = width * .5f
        val centerY = height * .46f
        coverBounds.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
        feather = minOf(coverBounds.width(), coverBounds.height()) * .055f

        val layer = canvas.saveLayer(coverBounds, null)
        canvas.drawBitmap(artwork, null, coverBounds, paint)
        val colors = intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT)
        val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        fun fadeEdge(x: Float, y: Float, extent: Float) {
            val fraction = feather / extent
            mask.shader = LinearGradient(coverBounds.left, coverBounds.top, x, y, colors,
                floatArrayOf(0f, fraction, 1f - fraction, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(coverBounds, mask)
        }
        fadeEdge(coverBounds.right, coverBounds.top, coverBounds.width())
        fadeEdge(coverBounds.left, coverBounds.bottom, coverBounds.height())
        canvas.restoreToCount(layer)
    }

    fun draw(
        canvas: Canvas, width: Float, height: Float, tilt: Tilt, level: Float,
        ripples: List<ImmersionDynamics.Ripple>, fullDetail: Boolean
    ) {
        val unit = minOf(width, height)
        if (unit <= 0f) return
        bounds.set(0f, 0f, width, height)

        var count = 0
        val surface = level * .008f + tilt.motion * .011f
        if (surface > .00025f) {
            waves[count++].set(tilt.x * .19f, tilt.y * .16f, .24f + level * .05f, .20f, surface, surface * 2.4f)
        }
        val limit = if (fullDetail) 5 else 2
        for (index in maxOf(0, ripples.size - limit) until ripples.size) {
            val ripple = ripples[index]
            val age = ripple.age
            val fade = (1f - exp(-age * 17f)) * exp(-age * 1.9f) * (1f - age / ripple.life)
            val amplitude = ripple.strength * fade * .067f
            waves[count++].set(
                ripple.x + tilt.x * age * .09f, ripple.y + tilt.y * age * .08f,
                .065f + age * (.41f + ripple.strength * .17f),
                .12f + age * .076f, amplitude, amplitude * 12f
            )
        }
        if (count == 0) {
            canvas.drawBitmap(texture, null, bounds, paint)
            return
        }

        val shortCells = if (fullDetail) 52 else 36
        val longCells = (maxOf(width, height) / unit * shortCells).roundToInt().coerceAtMost(if (fullDetail) 84 else 60)
        val columns = if (width > height) longCells else shortCells
        val rows = if (height > width) longCells else shortCells
        val centerX = width * .5f
        val centerY = height * .5f
        val twist = tilt.x * .23f - tilt.y * .18f
        val squeeze = 1f + tilt.y * .13f
        var vertex = 0
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            val py = height * v
            val y = (py - centerY) / unit
            val coverY = v * texture.height
            val insideY = minOf(coverY - coverBounds.top, coverBounds.bottom - coverY)
            val screenEdgeY = minOf(py, height - py)
            for (column in 0..columns) {
                val u = column.toFloat() / columns
                val px = width * u
                val x = (px - centerX) / unit
                var offsetX = 0f
                var offsetY = 0f
                var lightChange = 0f
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
                    val light = ((dx - dy) / distance * .22f + .68f).coerceIn(0f, 1f)
                    lightChange += crest * wave.lighting * light
                }
                val coverX = u * texture.width
                val inside = minOf(insideY, coverX - coverBounds.left, coverBounds.right - coverX)
                val coverWeight = (inside / feather).coerceIn(0f, 1f)
                val edge = (minOf(px, width - px, screenEdgeY) / (unit * .05f)).coerceIn(0f, 1f)
                val displacement = unit * edge * (1f - .58f * coverWeight)
                vertices[vertex * 2] = px + offsetX.coerceIn(-.04f, .04f) * displacement
                vertices[vertex * 2 + 1] = py + offsetY.coerceIn(-.04f, .04f) * displacement
                // Opposite slopes add a soft white rim or a neighboring dark trough.
                val alpha = (abs(lightChange).coerceAtMost(.22f) * (1f - .28f * coverWeight) * 255f).roundToInt()
                shading[vertex++] = (alpha shl 24) or (if (lightChange >= 0f) 0x00ffffff else 0)
            }
        }
        canvas.drawBitmapMesh(texture, columns, rows, vertices, 0, null, 0, paint)
        canvas.drawBitmapMesh(white, columns, rows, vertices, 0, shading, 0, paint)
    }
}

/** Built-in artwork for tracks without a cover. */
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
