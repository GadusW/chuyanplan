package com.stillune.app

import com.stillune.app.immersion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ImmersionDynamicsTest {
    @Test fun uprightHoldingUsesLocalPoseWithoutEulerFlips() {
        // Start exactly at world pitch 90 degrees, where absolute Euler roll is ambiguous.
        val origin = floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f)
        val pose = PoseReference()
        val initial = pose.rotation(origin)
        assertEquals(1f, initial[0], .0001f)
        assertEquals(1f, initial[4], .0001f)
        assertEquals(1f, initial[8], .0001f)
        // A small local yaw-free side tilt should remain small on either side of upright.
        for (angle in listOf(-.08f, .08f)) {
            val c = cos(angle); val s = sin(angle)
            val tilted = floatArrayOf(c,0f,s, s,0f,-c, 0f,1f,0f)
            val relative = pose.rotation(tilted)
            assertEquals(angle, atan2(relative[2], relative[8]), .0001f)
        }
        pose.reset()
        assertEquals(1f, pose.rotation(origin)[8], .0001f)
    }
    @Test fun silenceAndSteadyToneDoNotInventRepeatedBeats() {
        val detector = RhythmEnvelope()
        repeat(100) { assertEquals(0L, detector.update(0f, .02f).beatId) }
        val first = detector.update(.2f, .02f).beatId
        repeat(200) { assertEquals(first, detector.update(.2f, .02f).beatId) }
        repeat(100) { assertEquals(first, detector.update(0f, .02f).beatId) }
        assertEquals(0f, detector.update(Float.NaN, .02f).level, 0f)
    }

    @Test fun detectsQuietAndLoudAttacksWithoutClippingTheContrast() {
        fun hits(gain: Float): Pair<Long, Float> {
            val detector = RhythmEnvelope()
            var result = AudioEnergy()
            var minimum = 1f
            repeat(250) { i ->
                val phase = i % 25
                result = detector.update((if (phase in 5..7) .6f else .04f) * gain, .02f)
                if (phase == 20 && i > 25) minimum = minOf(minimum, result.level)
            }
            return result.beatId to minimum
        }
        val loud = hits(1f); val quiet = hits(.04f)
        assertEquals(10L, loud.first)
        assertEquals(loud.first, quiet.first)
        assertTrue(loud.second < .5f && quiet.second < .5f)
    }

    @Test fun attacksExplodeOutwardThenExpireAndPoolsStayBounded() {
        val scene = ImmersionDynamics()
        scene.advance(.016f, Tilt(), 0f, 1f)
        val mote = scene.motes.first()
        val radius = hypot(mote.x, mote.y)
        repeat(20) { scene.advance(.016f, Tilt(), 0f, 0f) }
        assertTrue(hypot(mote.x, mote.y) > radius + .04f)
        repeat(1000) { scene.advance(.016f, Tilt(), 0f, 1f) }
        assertEquals(176, scene.motes.size)
        repeat(240) { scene.advance(.016f, Tilt(), 0f, 0f) }
        assertTrue(scene.motes.all { it.age >= it.life })
        assertTrue(scene.pulse < .001f)
    }

    @Test fun handCurrentSteersParticlesWithFrameRateIndependentDecay() {
        val still = ImmersionDynamics(); val moving = ImmersionDynamics()
        still.advance(.01f, Tilt(), 0f, 1f); moving.advance(.01f, Tilt(), 0f, 1f)
        repeat(60) { still.advance(1f / 60, Tilt(), 0f, 0f) }
        repeat(120) { moving.advance(1f / 120, Tilt(x = .8f), 0f, 0f) }
        assertEquals(still.pulse, moving.pulse, .001f)
        assertTrue(moving.motes[0].x > still.motes[0].x + .05f)
    }

    @Test fun directionalShakesPushFromOppositeSidesAndSprayFallsBeforeTheWaterSettles() {
        val left = WaterSurface().apply { resize(360, 760) }
        val right = WaterSurface().apply { resize(360, 760) }
        left.advance(.016f, Tilt(motion = .8f, available = true, shakeX = .8f), 0f, 0f)
        right.advance(.016f, Tilt(motion = .8f, available = true, shakeX = -.8f), 0f, 0f)
        val leftDrops = left.drops.filter { it.age < it.life }
        val rightDrops = right.drops.filter { it.age < it.life }
        assertTrue(leftDrops.isNotEmpty() && leftDrops.all { it.u < .4f && it.dx > 0f })
        assertTrue(rightDrops.isNotEmpty() && rightDrops.all { it.u > .6f && it.dx < 0f })
        assertTrue(left.foam > .5f && right.foam > .5f)
        repeat(38) { left.advance(.016f, Tilt(), 0f, 0f) }
        assertTrue(left.active && left.drops.none { it.age < it.life })
        repeat(210) { left.advance(.016f, Tilt(), 0f, 0f) }
        assertFalse(left.active)
        assertEquals(0f, left.foam, 0f)
    }

    @Test fun waterPropagatesAtTheSameRateAcrossDisplayRefreshRates() {
        fun slopesAtHalfSecond(refresh: Int): List<Float> {
            val water = WaterSurface().apply { resize(360, 760) }
            water.advance(0f, Tilt(), 0f, 1f)
            repeat(refresh / 2) { water.advance(1f / refresh, Tilt(), 0f, 0f) }
            val out = FloatArray(2)
            return (0..8).flatMap { index ->
                water.sampleSlopes(.1f * (index + 1), .39f, out)
                out.toList()
            }
        }
        val fast = slopesAtHalfSecond(120)
        val slow = slopesAtHalfSecond(60)
        assertTrue(fast.any { abs(it) > .015f })
        fast.zip(slow).forEach { (a, b) -> assertEquals(a, b, .0001f) }
    }

    @Test fun quietPlaybackAddsFineRipplesWithoutInventingFoamOrDroplets() {
        val water = WaterSurface().apply { resize(360, 760) }
        repeat(85) { water.advance(.016f, Tilt(), 0f, 0f) }
        assertFalse(water.active)
        repeat(85) { water.advance(.016f, Tilt(), .54f, 0f) }
        assertTrue(water.active)
        assertEquals(0f, water.foam, 0f)
        assertTrue(water.drops.none { it.age < it.life })
    }

    @Test fun responsiveTiltSuppressesJitterButTracksIntentionalMovement() {
        val filter = ResponsiveTilt()
        repeat(50) { assertTrue(abs(filter.update(if (it % 2 == 0) .003f else -.003f, .02f)) < .002f) }
        repeat(4) { filter.update(.8f, .02f) }
        assertTrue(filter.update(.8f, .02f) > .72f)
    }
}
