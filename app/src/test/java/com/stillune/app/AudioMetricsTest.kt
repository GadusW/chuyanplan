package com.stillune.app

import com.stillune.app.analysis.*
import com.stillune.app.data.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class AudioMetricsTest {
    @Test fun fftFindsKnownFrequency() {
        val n = 2048; val bin = 37
        val power = Fft.power(DoubleArray(n) { sin(2*PI*bin*it/n) })
        assertEquals(bin, power.indices.maxBy { power[it] })
    }
    private fun metrics(kind: String): AudioMetrics {
        val rate = 44100; val accumulator = AudioAccumulator(6000, rate)
        repeat(rate*6) { i ->
            val left = .4*sin(2*PI*997*i/rate)
            val right = when(kind) { "mono" -> left; "anti" -> -left; "silence" -> 0.0; else -> .4*sin(2*PI*1301*i/rate) }
            accumulator.add(if (kind == "silence") 0.0 else left, right)
        }
        return accumulator.finish()
    }
    @Test fun monoAndAntiphaseHaveExpectedFieldAndBothRetainSpectralEnergy() {
        val mono = metrics("mono"); val anti = metrics("anti")
        assertEquals(1.0, mono.correlation!!, 1e-8); assertEquals(0.0, mono.width!!, 1e-8)
        assertEquals(-1.0, anti.correlation!!, 1e-8); assertEquals(1.0, anti.width!!, 1e-8)
        assertArrayEquals(mono.spectrum, anti.spectrum)
        assertTrue(anti.spectrum.any { it != 0.toByte() })
        assertEquals(2000, mono.sampleStartMs); assertEquals(4000, mono.sampleDurationMs)
        assertTrue(mono.drEstimate!! < .1) // TT-style RMS includes the sqrt(2) sine calibration.
    }
    @Test fun independentChannelsAreUncorrelatedAndSilenceIsNotAssignedADrScore() {
        val independent = metrics("independent")
        assertEquals(0.0, independent.correlation!!, .002)
        assertEquals(sqrt(.5), independent.width!!, .002)
        val silence = metrics("silence")
        assertNull(silence.drEstimate); assertNull(silence.correlation); assertNull(silence.width)
        assertTrue(silence.spectrum.all { it == 0.toByte() })
    }
    @Test fun browserKeepsAlbumsByDifferentArtistsSeparateAndFormatsCanonical() {
        val a = Track("one", "One", artist="A", album="Live", fileName="one.flac", source="test",format="flac")
        val b = a.copy(uri="two", artist="B")
        assertNotEquals(LibraryGrouping.key(a,"专辑"), LibraryGrouping.key(b,"专辑"))
        assertEquals("A", LibraryGrouping.key(a,"艺术家"))
        assertEquals("FLAC", LibraryGrouping.key(a,"格式"))
    }
}
