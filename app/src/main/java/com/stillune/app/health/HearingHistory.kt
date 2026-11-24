package com.stillune.app.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val LISTENING_CALENDAR_DAYS = 31

internal data class ListeningDay(
    val date: LocalDate,
    val listenedMs: Long = 0,
    val headphoneMs: Long = 0,
    val abovePreferenceMs: Long = 0,
)

internal data class HearingHistory(val days: List<ListeningDay>, val rollingWeekMs: Long)

internal fun hearingHistory(records: List<HearingMinute>, now: Long, zone: ZoneId): HearingHistory {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val firstDay = today.minusDays((LISTENING_CALENDAR_DAYS - 1).toLong())
    val firstMs = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStartMs = now - 7L * 86_400_000
    val listened = LongArray(LISTENING_CALENDAR_DAYS)
    val headphones = LongArray(LISTENING_CALENDAR_DAYS)
    val abovePreference = LongArray(LISTENING_CALENDAR_DAYS)
    var rollingWeekMs = 0L

    for (record in records) {
        if (record.startMs !in firstMs..now) continue
        val date = Instant.ofEpochMilli(record.startMs).atZone(zone).toLocalDate()
        val index = (date.toEpochDay() - firstDay.toEpochDay()).toInt()
        listened[index] += record.listenedMs
        headphones[index] += record.headphoneMs
        abovePreference[index] += record.abovePreferenceMs
        if (record.startMs >= weekStartMs) rollingWeekMs += record.listenedMs
    }

    return HearingHistory(
        days = List(LISTENING_CALENDAR_DAYS) { index ->
            ListeningDay(firstDay.plusDays(index.toLong()), listened[index], headphones[index], abovePreference[index])
        },
        rollingWeekMs = rollingWeekMs,
    )
}
