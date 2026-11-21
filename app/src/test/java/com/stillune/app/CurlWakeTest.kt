package com.stillune.app

import com.stillune.app.immersion.CurlWake
import com.stillune.app.immersion.Tilt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlWakeTest {
    @Test fun movingWaveSourceLeavesAContinuousTrailThatSettles() {
        val wake = CurlWake().apply { resize(360, 720) }
        for (step in 0..24) {
            wake.advance(.016f, Tilt(x = -.8f + step / 15f, motion = .75f, available = true), 0f)
        }
        assertTrue(wake.active)
        val before = wake.rasterize()
        val row = (wake.height * .49f).toInt() * wake.width
        val alongPath = (wake.width / 4..wake.width * 3 / 4).count { red(before[row + it]) > 30 }
        assertTrue(alongPath > wake.width / 3)
        assertTrue(before.all { green(it) == 0 })
        val peak = before.maxOf(::red)

        repeat(38) { wake.advance(.016f, Tilt(available = true), 0f) }
        assertTrue(wake.rasterize().maxOf(::red) < peak / 2)
        repeat(36) { wake.advance(.016f, Tilt(available = true), 0f) }
        assertFalse(wake.active)
        assertTrue(wake.rasterize().all { red(it) == 0 })
    }

    @Test fun onlyStrongAttacksOpenTheLocalCoverProtectionWhenStill() {
        val wake = CurlWake().apply { resize(360, 720) }
        val still = Tilt(available = true)
        repeat(45) { assertFalse(wake.advance(.016f, still, .35f)) }
        wake.advance(.016f, still, .96f)
        val pixels = wake.rasterize()
        assertTrue(wake.active)
        assertTrue(pixels.maxOf(::green) > 140)
        assertEquals(0, green(pixels.first()))
        wake.reset()
        assertFalse(wake.active)
    }

    private fun red(color: Int) = (color ushr 16) and 255
    private fun green(color: Int) = (color ushr 8) and 255
}
