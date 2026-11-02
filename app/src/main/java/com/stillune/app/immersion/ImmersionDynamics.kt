package com.stillune.app.immersion

import kotlin.math.*
import kotlin.random.Random

/** Bounded, time-based simulation. Coordinates are in units of the shortest screen side. */
class ImmersionDynamics {
    data class Ripple(val x: Float, val y: Float, val strength: Float, val seed: Float,
        var age: Float = 0f, val life: Float = 2.4f)
    class Mote {
        var x = 0f; var y = 0f; var oldX = 0f; var oldY = 0f
        var vx = 0f; var vy = 0f; var age = 0f; var life = 0f
        var size = 0f; var color = 0
    }
    val ripples = ArrayList<Ripple>(8)
    val motes = Array(176) { Mote() }
    private val random = Random(481)
    private var cursor = 0
    private var elapsed = 0f
    private var lastShake = -1f
    private var previousMotion = 0f
    private var dust = 0f
    var pulse = 0f
        private set

    fun advance(seconds: Float, tilt: Tilt, level: Float, impulse: Float) {
        val dt = seconds.coerceIn(0f, .05f)
        elapsed += dt
        pulse *= exp(-dt / .19f)
        val shakeHit = tilt.motion > .24f && tilt.motion - previousMotion > .035f && elapsed - lastShake > .23f
        previousMotion = tilt.motion
        val kick = maxOf(impulse, if (shakeHit) tilt.motion * .7f else 0f).coerceIn(0f, 1f)
        if (kick > 0f) {
            if (shakeHit) lastShake = elapsed
            pulse = maxOf(pulse, kick)
            if (ripples.size == 8) ripples.removeAt(0)
            ripples.add(Ripple(tilt.x * .12f + tilt.shakeX * .12f,
                tilt.y * .10f - tilt.shakeY * .12f, kick, random.nextFloat() * 6.28f))
            burst(24 + (kick * 32).toInt(), kick, tilt)
        }
        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            ripple.age += dt
            if (ripple.age >= ripple.life) iterator.remove()
        }
        // Fine spray moves with the music/hand; only detected attacks create big bursts.
        dust += dt * (level * 12f + tilt.motion * 16f)
        if (dust >= 1f) { burst(dust.toInt().coerceAtMost(3), .12f, tilt); dust %= 1f }
        for (mote in motes) {
            if (mote.age >= mote.life) continue
            mote.age += dt
            mote.oldX = mote.x; mote.oldY = mote.y
            val radius = hypot(mote.x, mote.y).coerceAtLeast(.2f)
            // Tilting creates a current; shaking injects directional spray and vorticity.
            mote.vx += (tilt.x * .35f + tilt.shakeX * 1.5f - mote.y / radius * tilt.motion * .24f) * dt
            mote.vy += (tilt.y * .32f - tilt.shakeY * 1.5f + mote.x / radius * tilt.motion * .24f + .035f) * dt
            val drag = exp(-dt * .65f)
            mote.vx *= drag; mote.vy *= drag
            mote.x += mote.vx * dt; mote.y += mote.vy * dt
        }
    }

    private fun burst(count: Int, strength: Float, tilt: Tilt) {
        repeat(count) {
            val mote = motes[cursor++ % motes.size]
            val angle = random.nextFloat() * (2f * PI.toFloat())
            val speed = .10f + random.nextFloat() * (.18f + strength * .9f)
            val radius = .25f + random.nextFloat() * .09f
            mote.x = cos(angle) * radius + tilt.x * .10f
            mote.y = sin(angle) * radius + tilt.y * .10f
            mote.oldX = mote.x; mote.oldY = mote.y
            mote.vx = cos(angle) * speed + tilt.shakeX * .9f
            mote.vy = sin(angle) * speed - tilt.shakeY * .9f
            mote.age = 0f
            mote.life = .75f + random.nextFloat() * (1.2f + strength * .7f)
            mote.size = .7f + random.nextFloat() * (1.3f + strength * 1.2f)
            mote.color = random.nextInt(4)
        }
    }

    companion object {
        /** Interfering low-frequency waves, bent by the hand's current (not noisy polygons). */
        fun contour(angle: Float, age: Float, seed: Float, strength: Float, tilt: Tilt): Float {
            val water = sin(angle * 3f + age * 2.7f + seed) * .048f +
                sin(angle * 5f - age * 1.8f + seed * .7f) * .023f +
                sin(angle * 2f - age * 2.2f) * .035f
            val current = (cos(angle) * tilt.x + sin(angle) * tilt.y) * .10f +
                sin(angle * 4f + seed + age * 3f) * tilt.motion * .055f
            return (water * (.4f + strength) + current) * (1f - exp(-age * 7f))
        }
    }
}
