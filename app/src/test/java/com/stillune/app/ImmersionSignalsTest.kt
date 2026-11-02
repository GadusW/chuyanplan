package com.stillune.app

import com.stillune.app.immersion.*
import org.junit.Assert.*
import org.junit.Test

class ImmersionSignalsTest {
    @Test fun rmsMeasuresActualUnsignedPcmAndSilence() {
        assertEquals(0f, WaveformEnergy.rms(ByteArray(512) { 128.toByte() }), .00001f)
        val signal = ByteArray(512) { if (it % 2 == 0) 192.toByte() else 64.toByte() }
        assertEquals(.5f, WaveformEnergy.rms(signal), .00001f)
        assertEquals(0f, WaveformEnergy.rms(byteArrayOf()), 0f)
    }
    @Test fun lowPassIsBoundedAndIndependentOfSamplingRate() {
        val slow = LowPass(.2f)
        val fast = LowPass(.2f)
        repeat(25) { slow.update(1f, .02f) }
        repeat(50) { fast.update(1f, .01f) }
        assertEquals(slow.value, fast.value, .0001f)
        assertTrue(slow.value in .9f..1f)
        val before = slow.value
        slow.update(Float.NaN, .02f)
        assertEquals(before, slow.value, 0f)
        assertTrue(LowPass().update(1f, .02f) < .2f)
    }
    @Test fun tripleTapRequiresThreeConsecutiveNearbyTapsAndResets() {
        val gate = TripleTapGate()
        assertFalse(gate.tap(1000))
        assertFalse(gate.tap(1200))
        assertTrue(gate.tap(1400))
        assertFalse(gate.tap(1600))
        assertFalse(gate.tap(3000))
        assertFalse(gate.tap(3200))
        assertTrue(gate.tap(3400))
    }
}
