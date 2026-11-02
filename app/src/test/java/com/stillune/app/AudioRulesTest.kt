package com.stillune.app

import com.stillune.app.data.*
import com.stillune.app.playback.*
import org.junit.Assert.*
import org.junit.Test

class AudioRulesTest {
    @Test fun formatsAreCaseInsensitiveAndM4aIsNotAssumedLossless() {
        listOf("song.MP3", "song.wav", "song.flac", "song.aac", "song.m4a", "song.ALAC").forEach {
            assertTrue(AudioFiles.accepts(it, null))
        }
        assertFalse(AudioFiles.accepts("image.png", "image/png"))
        assertFalse(AudioFiles.isLossless("M4A"))
        assertEquals("ALAC", AudioFiles.format("track.m4a", "audio/alac"))
        assertTrue(AudioFiles.isLossless("ALAC"))
    }
    @Test fun filtersKeepShortTracksByDefaultAndUnknownDurationIsNotDiscarded() {
        val short = Track("content://test/1", "Prelude", durationMs = 20000, sizeBytes = 90000, fileName = "prelude.flac", source = "test")
        assertTrue(short.passes(ScanOptions()))
        assertFalse(short.passes(ScanOptions(minDurationSeconds = 60)))
        assertFalse(short.passes(ScanOptions(minSizeKb = 100)))
        assertTrue(short.copy(durationMs = 0).passes(ScanOptions(minDurationSeconds = 60)))
    }
    @Test fun queueEndsOrRepeatsAccordingToSelectedMode() {
        assertNull(QueuePolicy.next(0, 0, RepeatMode.ALL, true))
        assertEquals(0, QueuePolicy.next(2, 3, RepeatMode.ALL, true))
        assertNull(QueuePolicy.next(2, 3, RepeatMode.OFF, true))
        assertEquals(1, QueuePolicy.next(1, 3, RepeatMode.ONE, true))
        assertEquals(2, QueuePolicy.next(1, 3, RepeatMode.ONE, false))
    }
}
