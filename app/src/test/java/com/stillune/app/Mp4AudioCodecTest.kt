package com.stillune.app

import com.stillune.app.data.Mp4AudioCodec
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import org.junit.Assert.*
import org.junit.Test

class Mp4AudioCodecTest {
    private fun box(type: String, content: ByteArray) = ByteBuffer.allocate(content.size + 8)
        .putInt(content.size + 8).put(type.toByteArray(Charsets.US_ASCII)).put(content).array()
    private fun detect(bytes: ByteArray): String? {
        val file = Files.createTempFile("stillune-codec", ".m4a")
        return try {
            Files.write(file, bytes)
            Files.newByteChannel(file, StandardOpenOption.READ).use { Mp4AudioCodec.detect(it) }
        } finally { Files.deleteIfExists(file) }
    }
    @Test fun codecComesFromSampleEntryEvenWhenMoovFollowsAudioData() {
        for ((entry, expected) in listOf("alac" to "audio/alac", "mp4a" to "audio/mp4a-latm")) {
            val stsd = box("stsd", ByteBuffer.allocate(8).putInt(0).putInt(1).array() + box(entry, ByteArray(28)))
            val moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd)))))
            assertEquals(expected, detect(box("mdat", ByteArray(256)) + moov))
        }
    }
    @Test fun malformedOrUnrelatedDataCannotBeMisidentifiedAsAlac() {
        assertNull(detect(box("mdat", "alac".toByteArray())))
        assertNull(detect(ByteBuffer.allocate(8).putInt(Int.MAX_VALUE).put("moov".toByteArray()).array()))
        assertNull(detect(byteArrayOf(0, 1, 2)))
    }
}
