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
import com.stillune.app.immersion.WaterSurface
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Folds the complete cover and its soft backdrop into one continuous surface for refraction. */
internal class CoverRefraction(artwork: Bitmap, viewportWidth: Int, viewportHeight: Int) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffedf5f6.toInt() }
    private val bounds = RectF()
    private val vertices = FloatArray(53 * 85 * 2)
    private val shading = IntArray(53 * 85)
    private val displacement = FloatArray(53 * 85)
    private val artworkWeight = FloatArray(53 * 85)
    private val foamTexture = FloatArray(53 * 85)
    private val slopes = FloatArray(2)
    private var meshWidth = 0f
    private var meshHeight = 0f
    private var meshColumns = 0
    private var meshRows = 0
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

    fun draw(canvas: Canvas, width: Float, height: Float, water: WaterSurface?, fullDetail: Boolean) {
        val unit = minOf(width, height)
        if (unit <= 0f) return
        bounds.set(0f, 0f, width, height)

        if (water == null || !water.active) {
            canvas.drawBitmap(texture, null, bounds, paint)
            return
        }

        val shortCells = if (fullDetail) 52 else 36
        val longCells = (maxOf(width, height) / unit * shortCells).roundToInt().coerceAtMost(if (fullDetail) 84 else 60)
        val columns = if (width > height) longCells else shortCells
        val rows = if (height > width) longCells else shortCells
        if (width != meshWidth || height != meshHeight || columns != meshColumns || rows != meshRows) {
            prepareMesh(width, height, columns, rows)
        }
        var vertex = 0
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            val py = height * v
            for (column in 0..columns) {
                val u = column.toFloat() / columns
                val px = width * u
                water.sampleSlopes(u, v, slopes)
                val dx = slopes[0]
                val dy = slopes[1]
                val weight = displacement[vertex]
                vertices[vertex * 2] = px + (dx * .106f).coerceIn(-.052f, .052f) * weight
                vertices[vertex * 2 + 1] = py + (dy * .106f).coerceIn(-.052f, .052f) * weight

                val litSlope = dx * .40f - dy * .67f
                val light = (litSlope * 2.13f).coerceIn(-.37f, .44f) * (1f - .25f * artworkWeight[vertex])
                val foamLight = (litSlope * 1.72f).coerceAtMost(.38f)
                val foam = if (water.foam > .005f && foamLight > .045f) {
                    val steepness = dx * dx + dy * dy
                    val crest = ((steepness - .014f) * 15f).coerceIn(0f, 1f)
                    val breaks = (crest * .86f + foamTexture[vertex] * .54f - .48f).coerceIn(0f, 1f)
                    breaks * water.foam * ((foamLight - .045f) * 7f).coerceIn(0f, 1f) * .48f
                } else 0f
                val tint = light + (1f - light) * foam
                val alpha = (abs(tint) * 255f).roundToInt()
                shading[vertex++] = (alpha shl 24) or (if (tint >= 0f) 0x00ffffff else 0)
            }
        }
        canvas.drawBitmapMesh(texture, columns, rows, vertices, 0, null, 0, paint)
        canvas.drawBitmapMesh(white, columns, rows, vertices, 0, shading, 0, paint)
        for (drop in water.drops) {
            if (drop.age >= drop.life) continue
            val phase = drop.age / drop.life
            val arc = 4f * phase * (1f - phase)
            val x = drop.u * width + drop.dx * phase * unit
            val y = drop.v * height + (drop.dy * phase - drop.lift * arc) * unit
            val radius = drop.radius * unit * (1f - phase * .46f)
            sprayPaint.alpha = (255f * .87f * (phase * 20f).coerceAtMost(1f) * (1f - phase)).roundToInt()
            canvas.drawOval(x - radius, y - radius * 1.3f, x + radius, y + radius * 1.3f, sprayPaint)
        }
    }

    private fun prepareMesh(width: Float, height: Float, columns: Int, rows: Int) {
        meshWidth = width
        meshHeight = height
        meshColumns = columns
        meshRows = rows
        val unit = minOf(width, height)
        var index = 0
        for (row in 0..rows) for (column in 0..columns) {
            val u = column.toFloat() / columns
            val v = row.toFloat() / rows
            val px = u * width
            val py = v * height
            val coverX = u * texture.width
            val coverY = v * texture.height
            val inside = minOf(coverX - coverBounds.left, coverBounds.right - coverX,
                coverY - coverBounds.top, coverBounds.bottom - coverY)
            val cover = (inside / feather).coerceIn(0f, 1f)
            val edge = (minOf(px, width - px, py, height - py) / (unit * .056f)).coerceIn(0f, 1f)
            artworkWeight[index] = cover
            displacement[index] = unit * edge * (1f - .5f * cover)
            // Two stable scales break the foam up without changing the propagation of the waves.
            val x = px / unit * 216f
            val y = py / unit * 216f
            val grain = sin(x * .67f + sin(y * .34f) * 1.7f) * sin(y * .71f - x * .19f)
            val breaks = sin(x * .26f - y * .20f) * sin(y * .27f + x * .17f)
            foamTexture[index++] = (breaks * .62f + grain * .29f + .49f).coerceIn(0f, 1f)
        }
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
