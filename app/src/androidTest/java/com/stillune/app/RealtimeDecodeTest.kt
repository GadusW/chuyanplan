package com.stillune.app

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.analysis.*
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class RealtimeDecodeTest {
    private class Enough : RuntimeException()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext

    @Test fun floatPcmReadsAllFiveFormatsAndCanRepeatedlyCancelWhileThePipeIsFull() = runBlocking {
        val before=File("/proc/self/fd").list()!!.size
        for (name in listOf("moon.mp3","moon.wav","moon.flac","moon.aac","moon-alac.m4a","moon.flac","moon-alac.m4a")) {
            val file=File(context.cacheDir,"live-$name")
            instrumentation.context.assets.open("audio/$name").use {src ->file.outputStream().use {src.copyTo(it)}}
            var blocks=0; var frame=RealtimeFrame()
            val processor=RealtimeProcessor()
            try {
                try {
                    withTimeout(20000) {
                        RealtimePcmDecoder(context,Dispatchers.IO).read(
                            Track(Uri.fromFile(file).toString(),name,fileName=name,source="test"),1200,
                            {PlaybackState(playing=true,positionMs=1200+blocks*100L)}) {pcm,count,_ ->
                            frame=processor.process(pcm,count); blocks++
                            if (blocks==6) throw Enough()
                        }
                    }
                } catch (_: Enough) { }
                assertEquals("blocks $name",6,blocks)
                assertNotNull("LUFS $name",frame.integrated)
                assertTrue("Valid float PCM $name",frame.levels.rmsL!! in -70.0..0.0)
                assertTrue("Stereo identity $name",frame.correlation!!>.99)
            } finally {file.delete()}
        }
        assertTrue("No accumulating pipe descriptor leak",File("/proc/self/fd").list()!!.size < before+18)
    }

    @Test fun startingAtThePlayheadSelectsTheActualLaterChannelSegment() = runBlocking {
        val file=File(context.cacheDir,"live-seek.wav")
        val rate=48000; val frames=rate*6; val bytes=ByteBuffer.allocate(44+frames*4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+frames*4).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames*4)
        repeat(frames) {i ->
            bytes.putShort(if (i<rate*2) (.3*sin(2*PI*1000*i/rate)*32767).toInt().toShort() else 0)
            bytes.putShort(if (i>=rate*2) (.2*sin(2*PI*2000*i/rate)*32767).toInt().toShort() else 0)
        }
        file.writeBytes(bytes.array())
        var blocks=0; var frame=RealtimeFrame()
        val processor=RealtimeProcessor(startMs=3500)
        try {
            try {
                withTimeout(20000) {
                    RealtimePcmDecoder(context,Dispatchers.IO).read(
                        Track(Uri.fromFile(file).toString(),"seek",fileName="seek.wav",source="test"),3500,
                        {PlaybackState(playing=true,positionMs=3500+blocks*100L)}) {pcm,count,_ ->
                        frame=processor.process(pcm,count); blocks++
                        if (blocks==6) throw Enough()
                    }
                }
            } catch (_: Enough) { }
            assertEquals(6,blocks)
            assertTrue("Seek must skip early left-channel tone",frame.levels.rmsL==null || frame.levels.rmsL!! < -80)
            assertEquals(-16.99,frame.levels.rmsR!!,.15)
            assertEquals(4100L,frame.positionMs)
        } finally {file.delete()}
    }
}
