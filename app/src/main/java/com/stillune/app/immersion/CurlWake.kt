package com.stillune.app.immersion

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Builds a low-resolution displacement field from motion along a virtual screen-space path. */
internal class CurlWake {
    private class Stamp {
        var x = 0f
        var y = 0f
        var radius = 0f
        var strength = 0f
        var accent = false
        var age = 0f
    }

    private val stamps = Array(32) { Stamp() }
    private val profile = FloatArray(193) { i ->
        val radius = i / 192f
        val edge = 1f - radius * radius
        edge * edge * (.55f + .45f * cos(radius * 8f * PI.toFloat()))
    }
    private var spanX = 0f
    private var spanY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hasPosition = false
    private var moving = false
    private var distanceFromStamp = 0f
    private var nextStamp = 0
    private var density = FloatArray(0)
    private var accent = FloatArray(0)
    private var pixels = IntArray(0)
    var width = 0
        private set
    var height = 0
        private set
    var active = false
        private set

    fun resize(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val unit = minOf(viewWidth, viewHeight).toFloat()
        val scale = minOf(96f / unit, 208f / maxOf(viewWidth, viewHeight))
        val newWidth = (viewWidth * scale).roundToInt().coerceAtLeast(2)
        val newHeight = (viewHeight * scale).roundToInt().coerceAtLeast(2)
        val newSpanX = viewWidth / unit
        val newSpanY = viewHeight / unit
        if (newWidth == width && newHeight == height && newSpanX == spanX && newSpanY == spanY) return
        width = newWidth
        height = newHeight
        spanX = newSpanX
        spanY = newSpanY
        density = FloatArray(width * height)
        accent = FloatArray(width * height)
        pixels = IntArray(width * height)
        reset()
    }

    fun reset() {
        for (stamp in stamps) stamp.strength = 0f
        active = false
        hasPosition = false
        moving = false
        distanceFromStamp = 0f
        nextStamp = 0
    }

    /** Returns true for the final settling frame as well, so it removes the previous effect. */
    fun advance(dt: Float, tilt: Tilt, impulse: Float): Boolean {
        if (width == 0) return false
        val wasActive = active
        active = false
        for (stamp in stamps) {
            if (stamp.strength == 0f) continue
            stamp.age += dt
            if (stamp.age >= 1.12f) stamp.strength = 0f
            else active = true
        }

        val x = (spanX * .5f + tilt.x * .32f + tilt.shakeX * .24f).coerceIn(spanX * .09f, spanX * .91f)
        val y = (spanY * .49f + tilt.y * .32f - tilt.shakeY * .24f).coerceIn(spanY * .09f, spanY * .91f)
        if (tilt.available && hasPosition && tilt.motion > .07f) {
            val dx = x - lastX
            val dy = y - lastY
            val length = hypot(dx, dy)
            val strength = .41f + .29f * tilt.motion.coerceIn(0f, 1f)
            if (length >= .0015f) {
                if (!moving && length >= .009f) {
                    add(lastX, lastY, .13f, strength, false)
                    moving = true
                }
                var along = SPACING - distanceFromStamp
                while (along <= length) {
                    val fraction = along / length
                    add(lastX + dx * fraction, lastY + dy * fraction, .13f, strength, false)
                    along += SPACING
                    moving = true
                }
                distanceFromStamp = (length + SPACING - along).coerceIn(0f, SPACING)
            }
        } else {
            moving = false
            distanceFromStamp = 0f
        }
        lastX = x
        lastY = y
        hasPosition = tilt.available

        if (impulse >= .55f) {
            val amount = impulse.coerceAtMost(1f)
            add(x, y, .16f + .07f * amount, 1.8f * amount, true)
        }
        return wasActive || active
    }

    /** Red encodes the combined displacement, green the local strong-beat contribution. */
    fun rasterize(): IntArray {
        density.fill(0f)
        accent.fill(0f)
        val scaleX = width / spanX
        val scaleY = height / spanY
        for (stamp in stamps) {
            if (stamp.strength == 0f) continue
            val radius = stamp.radius * (1f + 1.4f * (1f - exp(-3.5f * stamp.age)))
            val weight = stamp.strength * exp(-4.9f * stamp.age)
            val centerX = stamp.x * scaleX - .5f
            val centerY = stamp.y * scaleY - .5f
            val radiusX = radius * scaleX
            val radiusY = radius * scaleY
            val minX = floor(centerX - radiusX).toInt().coerceAtLeast(0)
            val maxX = ceil(centerX + radiusX).toInt().coerceAtMost(width - 1)
            val minY = floor(centerY - radiusY).toInt().coerceAtLeast(0)
            val maxY = ceil(centerY + radiusY).toInt().coerceAtMost(height - 1)
            for (y in minY..maxY) {
                val dy = (y - centerY) / radiusY
                for (x in minX..maxX) {
                    val dx = (x - centerX) / radiusX
                    val distance = dx * dx + dy * dy
                    if (distance >= 1f) continue
                    val radial = sqrt(distance) * (profile.size - 1)
                    val ring = radial.toInt().coerceAtMost(profile.lastIndex - 1)
                    val value = (profile[ring] + (profile[ring + 1] - profile[ring]) * (radial - ring)) * weight
                    val i = y * width + x
                    density[i] += value
                    if (stamp.accent) accent[i] += value
                }
            }
        }
        for (i in pixels.indices) {
            // The soft ceiling leaves the overlap gradients visible in a dense wake.
            val red = (255f * density[i] / (.72f + density[i])).roundToInt()
            val green = (255f * accent[i] / (.43f + accent[i])).roundToInt()
            pixels[i] = 0xff000000.toInt() or (red shl 16) or (green shl 8)
        }
        return pixels
    }

    private fun add(x: Float, y: Float, radius: Float, strength: Float, accent: Boolean) {
        val stamp = stamps[nextStamp]
        nextStamp = (nextStamp + 1) % stamps.size
        stamp.x = x
        stamp.y = y
        stamp.radius = radius
        stamp.strength = strength
        stamp.accent = accent
        stamp.age = 0f
        active = true
    }

    private companion object {
        const val SPACING = .034f
    }
}
