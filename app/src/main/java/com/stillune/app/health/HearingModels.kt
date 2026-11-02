package com.stillune.app.health

import kotlin.math.min

data class HearingSettings(
    val enabled: Boolean = false,
    val breakMinutes: Int = 40,
    val volumePercent: Int = 60,
    val pauseOnReminder: Boolean = false,
    val headphoneName: String = "",
    val isolation: String = "尚未填写",
)

enum class HearingNoticeKind { BREAK, VOLUME, ENVIRONMENT }
data class HearingNotice(val id: Long, val kind: HearingNoticeKind, val title: String, val message: String)
data class HearingLive(
    val continuousMs: Long = 0,
    val volumeStep: Int = 0,
    val volumeMax: Int = 0,
    val headphonesConnected: Boolean = false,
    val notice: HearingNotice? = null,
    val storageError: Boolean = false,
    val unobservedMs: Long = 0,
)

data class EnvironmentReading(
    val running: Boolean = false,
    val changeDb: Float? = null,
    val status: String = "尚未开启",
)

/** Actual elapsed playback, independent of song position, seeks, and wall-clock changes. */
internal class ListeningClock {
    data class Sample(val elapsed: Long, val wall: Long, val active: Boolean, val headphones: Boolean, val above: Boolean)
    data class Slice(val minute: Long, val duration: Long, val headphones: Boolean, val above: Boolean)
    data class Result(val slices: List<Slice>, val missingMs: Long = 0)
    private var previous: Sample? = null
    fun reset() { previous = null }
    fun advance(next: Sample): Result {
        val old = previous
        previous = next
        if (old == null || !old.active) return Result(emptyList())
        val delta = next.elapsed - old.elapsed
        if (delta <= 0) return Result(emptyList())
        // Never fill a process/scheduling gap by assuming that music continued.
        if (delta > 10_000) return Result(emptyList(), delta)
        val slices = mutableListOf<Slice>()
        var cursor = old.wall
        var remaining = delta
        while (remaining > 0) {
            val minute = Math.floorDiv(cursor, 60_000L) * 60_000L
            val count = min(remaining, minute + 60_000 - cursor)
            slices += Slice(minute, count, old.headphones, old.above)
            cursor += count
            remaining -= count
        }
        return Result(slices)
    }
}
