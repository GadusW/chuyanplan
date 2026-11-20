package com.stillune.app.immersion

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A shared height field with a fixed simulation step and a damped border. Owned by the frame clock. */
internal class WaterSurface {
    class Drop {
        var u = 0f
        var v = 0f
        var dx = 0f
        var dy = 0f
        var lift = 0f
        var radius = 0f
        var age = 0f
        var life = 0f
    }

    private var columns = 0
    private var rows = 0
    private var heights = FloatArray(0)
    private var velocities = FloatArray(0)
    private var slopeX = FloatArray(0)
    private var slopeY = FloatArray(0)
    private var damping = FloatArray(0)
    private var random = Random(481)
    private var nextDrop = 0
    private var remaining = 0f
    private var remainder = 0.0
    private var fineEnergy = 0f
    private var nextFineWave = .63f
    private var previousMotion = 0f
    private var shakeCooldown = 0f
    val drops = Array(28) { Drop() }
    var foam = 0f
        private set
    var revision = 0L
        private set
    val active: Boolean get() = remaining > 0f

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val scale = minOf(68f / minOf(width, height), 160f / maxOf(width, height))
        val newColumns = (width * scale).roundToInt().coerceAtLeast(6)
        val newRows = (height * scale).roundToInt().coerceAtLeast(6)
        if (newColumns == columns && newRows == rows) return
        columns = newColumns
        rows = newRows
        val cells = columns * rows
        heights = FloatArray(cells)
        velocities = FloatArray(cells)
        slopeX = FloatArray(cells)
        slopeY = FloatArray(cells)
        damping = FloatArray(cells) { index ->
            val x = index % columns
            val y = index / columns
            val edge = minOf(x, columns - 1 - x, y, rows - 1 - y)
            val absorb = (1f - edge / 9f).coerceAtLeast(0f)
            .993f - absorb * absorb * .19f
        }
        reset()
    }

    fun reset() {
        if (active) {
            heights.fill(0f)
            velocities.fill(0f)
            slopeX.fill(0f)
            slopeY.fill(0f)
        }
        drops.forEach { it.life = 0f }
        random = Random(481)
        nextDrop = 0
        remaining = 0f
        remainder = 0.0
        fineEnergy = 0f
        nextFineWave = .63f
        previousMotion = 0f
        shakeCooldown = 0f
        foam = 0f
        revision++
    }

    fun advance(seconds: Float, tilt: Tilt, energy: Float, impulse: Float) {
        if (columns == 0) return
        val dt = seconds.coerceIn(0f, .05f)
        val motion = if (tilt.available) tilt.motion else 0f
        shakeCooldown -= dt
        val crossed = previousMotion <= .24f || motion - previousMotion > dt * 1.8f
        val shake = motion > .24f && crossed && shakeCooldown <= 0f
        previousMotion = motion

        if (impulse > 0f) {
            val strength = impulse.coerceIn(0f, 1f)
            val u = (.5f + tilt.x * .11f + tilt.shakeX * .07f + (random.nextFloat() - .5f) * .17f).coerceIn(.18f, .82f)
            val v = (.52f + tilt.y * .08f - tilt.shakeY * .05f + (random.nextFloat() - .5f) * .13f).coerceIn(.2f, .8f)
            val radius = 3.7f + strength * 1.05f
            disturb(u, v, radius, radius, 0f, strength * 2.2f, directional = false)
            if (strength >= .55f) splash(u, v, strength, null)
        }
        if (shake) {
            shakeCooldown = .24f
            val motionX = tilt.shakeX + tilt.x * .35f
            val motionY = -tilt.shakeY + tilt.y * .35f
            val strength = (.42f + motion * .63f).coerceAtMost(1f)
            if (hypot(motionX, motionY) > .04f) {
                val direction = atan2(motionY, motionX)
                val span = minOf(columns, rows)
                val u = .5f - cos(direction) * .27f * span / columns
                val v = .5f - sin(direction) * .27f * span / rows
                disturb(u, v, 11f, 4.1f, direction, strength * 3.05f, directional = true)
                if (strength >= .55f) splash(u, v, strength, direction)
            } else {
                disturb(.5f, .52f, 5f, 5f, 0f, strength * 1.55f, directional = false)
                if (strength >= .55f) splash(.5f, .52f, strength, null)
            }
        }

        val fineStrength = (energy * .84f + motion * .43f).coerceIn(0f, 1f)
        if (fineStrength > .025f) {
            fineEnergy += dt * fineStrength * (1.1f + 3.2f * fineStrength)
            if (fineEnergy >= nextFineWave) {
                fineEnergy -= nextFineWave
                nextFineWave = .55f + random.nextFloat() * .55f
                val u = (.24f + random.nextFloat() * .52f + tilt.x * .09f).coerceIn(.13f, .87f)
                val v = (.29f + random.nextFloat() * .43f + tilt.y * .08f).coerceIn(.15f, .85f)
                disturb(u, v, 2.6f, 3.1f, .4f, .65f * fineStrength, directional = false)
            }
        } else fineEnergy = 0f

        if (!active) return
        if (remaining <= dt) {
            // The absorbing border and velocity damping have removed the visible wave by now.
            heights.fill(0f)
            velocities.fill(0f)
            slopeX.fill(0f)
            slopeY.fill(0f)
            remaining = 0f
            remainder = 0.0
            foam = 0f
        } else {
            remaining -= dt
            remainder += dt
            while (remainder >= STEP) {
                step()
                remainder -= STEP
            }
            for (y in 1 until rows - 1) for (x in 1 until columns - 1) {
                val i = y * columns + x
                slopeX[i] = (heights[i + 1] - heights[i - 1]) * .5f
                slopeY[i] = (heights[i + columns] - heights[i - columns]) * .5f
            }
        }
        revision++
    }

    fun sampleSlopes(u: Float, v: Float, out: FloatArray) {
        val gx = 1f + u * (columns - 4)
        val gy = 1f + v * (rows - 4)
        val x = gx.toInt()
        val y = gy.toInt()
        val a = y * columns + x
        val b = a + columns
        val fx = gx - x
        val fy = gy - y
        fun sample(values: FloatArray): Float {
            val top = values[a] + (values[a + 1] - values[a]) * fx
            val bottom = values[b] + (values[b + 1] - values[b]) * fx
            return top + (bottom - top) * fy
        }
        out[0] = sample(slopeX)
        out[1] = sample(slopeY)
    }

    private fun disturb(u: Float, v: Float, cross: Float, along: Float, angle: Float, strength: Float, directional: Boolean) {
        val centerX = u * (columns - 1)
        val centerY = v * (rows - 1)
        val dx = cos(angle)
        val dy = sin(angle)
        val margin = ceil(3.2f * maxOf(cross, along)).toInt()
        for (y in maxOf(1, centerY.toInt() - margin)..minOf(rows - 2, centerY.toInt() + margin)) {
            for (x in maxOf(1, centerX.toInt() - margin)..minOf(columns - 2, centerX.toInt() + margin)) {
                val offsetX = x - centerX
                val offsetY = y - centerY
                val longitudinal = (offsetX * dx + offsetY * dy) / along
                val lateral = (-offsetX * dy + offsetY * dx) / cross
                val distance = longitudinal * longitudinal + lateral * lateral
                if (distance >= 10f) continue
                val shape = exp(-distance * .5f) * strength
                val i = y * columns + x
                if (directional) {
                    heights[i] += longitudinal * shape
                    // Seed the matching velocity so a shove travels along the hand's direction.
                    velocities[i] -= .48f * (1f - longitudinal * longitudinal) * shape / along
                } else heights[i] += (1f - .46f * distance) * shape
            }
        }
        remaining = 3.2f
    }

    private fun splash(u: Float, v: Float, strength: Float, direction: Float?) {
        foam = maxOf(foam, strength)
        repeat(4 + (strength * 3).toInt()) {
            val drop = drops[nextDrop]
            nextDrop = (nextDrop + 1) % drops.size
            val angle = if (direction == null) random.nextFloat() * (2f * PI.toFloat())
                else direction + (random.nextFloat() - .5f) * 1.7f
            val reach = (.035f + random.nextFloat() * .10f) * strength
            val span = minOf(columns, rows)
            drop.u = u + (random.nextFloat() - .5f) * .05f * span / columns
            drop.v = v + (random.nextFloat() - .5f) * .04f * span / rows
            drop.dx = cos(angle) * reach
            drop.dy = sin(angle) * reach * .65f
            drop.lift = (.028f + random.nextFloat() * .089f) * strength
            drop.radius = .0031f + random.nextFloat() * .0048f
            drop.age = 0f
            drop.life = .28f + random.nextFloat() * .22f
        }
    }

    private fun step() {
        for (y in 1 until rows - 1) for (x in 1 until columns - 1) {
            val i = y * columns + x
            val laplacian = heights[i - 1] + heights[i + 1] + heights[i - columns] + heights[i + columns] - 4f * heights[i]
            velocities[i] = (velocities[i] + .235f * laplacian) * damping[i]
        }
        for (i in columns until heights.size - columns) heights[i] += velocities[i]
        foam *= .992f
        for (drop in drops) if (drop.age < drop.life) drop.age += STEP.toFloat()
    }

    private companion object {
        const val STEP = 1.0 / 120.0
    }
}
