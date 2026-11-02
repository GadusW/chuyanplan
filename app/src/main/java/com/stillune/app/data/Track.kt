package com.stillune.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String = "未知艺术家",
    val album: String = "本地音乐",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val fileName: String,
    val folder: String = "",
    val source: String,
    val format: String = "",
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val identity: String = uri,
    @ColumnInfo(defaultValue = "0") val modifiedAt: Long = 0,
) {
    val lossless: Boolean get() = AudioFiles.isLossless(format)
}

object AudioFiles {
    val extensions = setOf("mp3", "wav", "wave", "flac", "aac", "m4a", "mp4", "alac", "aif", "aiff", "ogg", "opus", "wma", "ape", "wv")
    fun accepts(name: String, mime: String?): Boolean =
        name.substringAfterLast('.', "").lowercase() in extensions || mime?.startsWith("audio/") == true
    fun isLossless(format: String) = format.uppercase() in setOf("FLAC", "ALAC", "PCM", "WAV", "WAVE", "AIFF", "AIF", "APE", "WV")
    fun format(name: String, codec: String? = null): String = when {
        codec?.contains("alac", true) == true -> "ALAC"
        codec?.contains("flac", true) == true -> "FLAC"
        codec?.contains("mp4a", true) == true || codec?.contains("aac", true) == true -> "AAC"
        else -> name.substringAfterLast('.', "音频").uppercase()
    }
}

data class ScanOptions(val minDurationSeconds: Int = 0, val minSizeKb: Int = 0)
fun Track.passes(options: ScanOptions) =
    (durationMs == 0L || durationMs >= options.minDurationSeconds * 1000L) &&
        (sizeBytes == 0L || sizeBytes >= options.minSizeKb * 1024L)
