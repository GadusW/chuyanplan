package com.stillune.app

import com.stillune.app.health.HearingMinute
import com.stillune.app.health.ListeningClock
import com.stillune.app.health.hearingHistory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HearingRulesTest {
    @Test fun calendarUsesLocalMidnightWhileRollingWeekUsesElapsedTime() {
        val zone = ZoneId.of("America/New_York")
        fun time(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
        val history = hearingHistory(
            listOf(
                HearingMinute(time("2025-03-10T00:31"), 60_000),
                HearingMinute(time("2025-03-10T00:00"), 10_000, 8_000, 2_000),
                HearingMinute(time("2025-03-09T23:59"), 20_000),
                HearingMinute(time("2025-03-02T23:30"), 30_000),
                HearingMinute(time("2025-03-02T23:29"), 40_000),
                HearingMinute(time("2025-02-08T00:00"), 50_000),
                HearingMinute(time("2025-02-07T23:59"), 60_000),
            ),
            now = time("2025-03-10T00:30"),
            zone = zone,
        )

        assertEquals(31, history.days.size)
        assertEquals(LocalDate.parse("2025-02-08"), history.days.first().date)
        assertEquals(50_000L, history.days.first().listenedMs)
        assertEquals(0L, history.days[1].listenedMs)
        assertEquals(20_000L, history.days[29].listenedMs)
        val today = history.days.last()
        assertEquals(LocalDate.parse("2025-03-10"), today.date)
        assertEquals(10_000L, today.listenedMs)
        assertEquals(8_000L, today.headphoneMs)
        assertEquals(2_000L, today.abovePreferenceMs)
        assertEquals(60_000L, history.rollingWeekMs)
    }

    @Test fun playbackUsesElapsedTimeAndSplitsMidnightWithoutDependingOnSongPosition() {
        val clock = ListeningClock()
        clock.advance(ListeningClock.Sample(1000, 86_399_500, true, true, false))
        val result = clock.advance(ListeningClock.Sample(2000, 86_400_500, true, true, false))
        assertEquals(listOf(500L, 500L), result.slices.map { it.duration })
        assertEquals(listOf(86_340_000L, 86_400_000L), result.slices.map { it.minute })
        assertEquals(1000L, result.slices.sumOf { it.duration })
    }
    @Test fun pauseMuteAndDisabledIntervalsAreNotCountedButLastPlayingIntervalIsFlushed() {
        val clock = ListeningClock()
        clock.advance(ListeningClock.Sample(1000, 1000, true, true, true))
        assertEquals(500L, clock.advance(ListeningClock.Sample(1500, 1500, false, true, true)).slices.sumOf { it.duration })
        assertTrue(clock.advance(ListeningClock.Sample(8000, 8000, false, true, true)).slices.isEmpty())
        assertTrue(clock.advance(ListeningClock.Sample(8500, 8500, true, false, false)).slices.isEmpty())
        val next = clock.advance(ListeningClock.Sample(9000, 9000, true, false, false)).slices.single()
        assertEquals(500L, next.duration)
        assertFalse(next.headphones); assertFalse(next.above)
    }
    @Test fun wallClockJumpDoesNotCreateOrRemoveHoursOfExposure() {
        val clock = ListeningClock()
        clock.advance(ListeningClock.Sample(1000, 3_600_000, true, true, false))
        val result = clock.advance(ListeningClock.Sample(1500, 1, true, true, false))
        assertEquals(500L, result.slices.sumOf { it.duration })
        assertEquals(3_600_000L, result.slices.single().minute)
    }
    @Test fun unobservedGapIsMarkedRatherThanFilledAndRestartHasNoPhantomTime() {
        val clock = ListeningClock()
        clock.advance(ListeningClock.Sample(1000, 1000, true, false, false))
        val gap = clock.advance(ListeningClock.Sample(61_000, 61_000, true, false, false))
        assertTrue(gap.slices.isEmpty()); assertEquals(60_000L, gap.missingMs)
        clock.reset()
        assertTrue(clock.advance(ListeningClock.Sample(90_000, 90_000, true, false, false)).slices.isEmpty())
    }
}
