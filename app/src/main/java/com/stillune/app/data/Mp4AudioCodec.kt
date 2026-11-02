package com.stillune.app.data

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** Reads the audio sample entry, not the filename: M4A can hold AAC or ALAC. */
object Mp4AudioCodec {
    fun detect(channel: SeekableByteChannel): String? {
        var budget = 10000
        fun boxes(start: Long, end: Long, depth: Int): String? {
            if (depth > 8) return null
            var pos = start
            while (pos + 8 <= end && budget-- > 0) {
                channel.position(pos)
                val header = ByteBuffer.allocate(8)
                while (header.hasRemaining()) if (channel.read(header) < 0) return null
                header.flip()
                var size = header.int.toLong() and 0xffffffffL
                val typeBytes = ByteArray(4); header.get(typeBytes)
                val type = String(typeBytes, Charsets.US_ASCII)
                var headerSize = 8L
                if (size == 1L) {
                    val large = ByteBuffer.allocate(8)
                    while (large.hasRemaining()) if (channel.read(large) < 0) return null
                    large.flip(); size = large.long; headerSize = 16
                } else if (size == 0L) size = end - pos
                if (size < headerSize || size > end - pos) return null
                if (type == "alac") return "audio/alac"
                if (type == "mp4a") return "audio/mp4a-latm"
                if (type in setOf("moov", "trak", "mdia", "minf", "stbl", "stsd")) {
                    val result = boxes(pos + headerSize + if (type == "stsd") 8 else 0, pos + size, depth + 1)
                    if (result != null) return result
                }
                pos += size
            }
            return null
        }
        return boxes(0, channel.size(), 0)
    }
}
