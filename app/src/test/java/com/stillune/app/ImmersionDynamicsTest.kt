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
        assertEquals(1, scene.ripples.size)
        val mote = scene.motes.first()
        val radius = hypot(mote.x, mote.y)
        repeat(20) { scene.advance(.016f, Tilt(), 0f, 0f) }
        assertTrue(hypot(mote.x, mote.y) > radius + .04f)
        repeat(1000) { scene.advance(.016f, Tilt(), 0f, 1f) }
        assertEquals(176, scene.motes.size)
        assertTrue(scene.ripples.size <= 8)
        repeat(240) { scene.advance(.016f, Tilt(), 0f, 0f) }
        assertTrue(scene.ripples.isEmpty())
        assertTrue(scene.motes.all { it.age >= it.life })
        assertTrue(scene.pulse < .001f)
    }

    @Test fun handCurrentBendsWavesAndSteersParticlesWithFrameRateIndependentDecay() {
        val still = ImmersionDynamics(); val moving = ImmersionDynamics()
        still.advance(.01f, Tilt(), 0f, 1f); moving.advance(.01f, Tilt(), 0f, 1f)
        repeat(60) { still.advance(1f / 60, Tilt(), 0f, 0f) }
        repeat(120) { moving.advance(1f / 120, Tilt(x = .8f), 0f, 0f) }
        assertEquals(still.pulse, moving.pulse, .001f)
        assertTrue(moving.motes[0].x > still.motes[0].x + .05f)
        val points = (0..40).map { ImmersionDynamics.contour(it * .157f, .6f, 1.3f, 1f, Tilt(.7f, .4f, .5f)) }
        assertTrue(points.max() - points.min() > .12f)
        assertTrue(points.all { it.isFinite() && abs(it) < .35f })
    }

    @Test fun responsiveTiltSuppressesJitterButTracksIntentionalMovement() {
        val filter = ResponsiveTilt()
        repeat(50) { assertTrue(abs(filter.update(if (it % 2 == 0) .003f else -.003f, .02f)) < .002f) }
        repeat(4) { filter.update(.8f, .02f) }
        assertTrue(filter.update(.8f, .02f) > .72f)
    }
}
