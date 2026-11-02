package com.stillune.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import com.stillune.app.analysis.NativePcmAnalyzer
import com.stillune.app.data.Track
import android.net.Uri

class NativeDecodeProbeTest {
    @Test fun offlinePcmExportIncludesAlacWithoutOpeningAnAudioOutput() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetContext = InstrumentationRegistry.getInstrumentation().context
        for (name in listOf("moon.mp3", "moon.wav", "moon.flac", "moon.aac", "moon-alac.m4a")) {
            val input = File(context.cacheDir, "probe-$name")
            assetContext.assets.open("audio/$name").use { src -> input.outputStream().use { src.copyTo(it) } }
            try {
                val track = Track(Uri.fromFile(input).toString(), name, fileName = name, source = "test", durationMs = 6000)
                val result = withTimeout(30000) { NativePcmAnalyzer(context, Dispatchers.IO).analyze(track) }
                assertTrue("PCM duration for $name", result.metrics.durationMs in 5500..6500)
                assertTrue("Representative PCM for $name", result.metrics.sampleDurationMs > 3000)
                assertNotNull(result.metrics.drEstimate)
                assertTrue("Mono-compatible fixture for $name", (result.metrics.correlation ?: 0.0) > .99)
                assertTrue(result.metrics.spectrum.any { it.toInt() != 0 })
                assertTrue(result.metrics.scatter.isNotEmpty())
            } finally { input.delete() }
        }
    }
}
