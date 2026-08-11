package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 10) // Monday
    private fun log(epochDay: Long) = LogEntry(id = 0, trackerId = 0, epochDay = epochDay)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    // ---- period boundaries ----

    @Test fun weekStart_isMonday() {
        assertEquals(LocalDate.of(2026, 8, 10), StatsCalculator.periodStart(date(2026, 8, 10), FrequencyPeriod.WEEK))
        assertEquals(LocalDate.of(2026, 8, 10), StatsCalculator.periodStart(date(2026, 8, 14), FrequencyPeriod.WEEK))
    }

    @Test fun weekEnd_isSunday() {
        assertEquals(LocalDate.of(2026, 8, 16), StatsCalculator.periodEnd(date(2026, 8, 12), FrequencyPeriod.WEEK))
    }

    @Test fun monthBoundaries() {
        assertEquals(LocalDate.of(2026, 8, 1), StatsCalculator.periodStart(today, FrequencyPeriod.MONTH))
        assertEquals(LocalDate.of(2026, 8, 31), StatsCalculator.periodEnd(today, FrequencyPeriod.MONTH))
    }

    @Test fun dayBoundaries_equalToday() {
        assertEquals(today, StatsCalculator.periodStart(today, FrequencyPeriod.DAY))
        assertEquals(today, StatsCalculator.periodEnd(today, FrequencyPeriod.DAY))
    }

    // ---- status (projected pace) ----

    private fun tracker(min: Int?, max: Int?, period: FrequencyPeriod = FrequencyPeriod.WEEK) =
        Triple(min, max, period)

    private fun status(logs: List<LogEntry>, t: Triple<Int?, Int?, FrequencyPeriod>): StatsCalculator.TrackStatus =
        StatsCalculator.statusFor(logs, t.first, t.second, t.third, today)

    @Test fun mondayTwoLogs_ofThreeToFivePerWeek_isHigh() {
        // 2 logs on day 1/7 → projected 14 → exceeds 5
        val t = tracker(3, 5)
        val s = status(listOf(log(day(2026, 8, 10)), log(day(2026, 8, 10))), t)
        assertEquals(StatsCalculator.Status.HIGH, s.status)
        assertEquals(2, s.count)
        assertEquals(14, s.projected)
    }

    @Test fun mondayNoLogs_isLow() {
        val t = tracker(3, 5)
        val s = status(emptyList(), t)
        assertEquals(StatsCalculator.Status.LOW, s.status)
        assertEquals(0, s.projected)
    }

    @Test fun fridayThreeLogs_ofThreeToFive_isOnTrack() {
        // Friday 2026-08-14: elapsed 5/7 → projected 3/(5/7)=4.2 → 4 ∈ [3,5]
        val friday = LocalDate.of(2026, 8, 14)
        val t = tracker(3, 5)
        val s = StatsCalculator.statusFor(
            listOf(log(day(2026, 8, 14)), log(day(2026, 8, 14)), log(day(2026, 8, 14))),
            t.first, t.second, t.third, friday
        )
        assertEquals(StatsCalculator.Status.ON_TRACK, s.status)
        assertEquals(4, s.projected)
    }

    @Test fun maxOnly_threeLogsFriday_isHigh() {
        val t = tracker(null, 2)
        val s = status(listOf(log(day(2026, 8, 14)), log(day(2026, 8, 14)), log(day(2026, 8, 14))), t)
        assertEquals(StatsCalculator.Status.HIGH, s.status)
    }

    @Test fun minOnly_oneLogFriday_isLow() {
        val friday = LocalDate.of(2026, 8, 14)
        val t = tracker(3, null)
        val s = StatsCalculator.statusFor(
            listOf(log(day(2026, 8, 14))),
            t.first, t.second, t.third, friday
        )
        assertEquals(StatsCalculator.Status.LOW, s.status)
    }

    @Test fun noRange_alwaysNoRange() {
        val t = tracker(null, null)
        assertEquals(StatsCalculator.Status.NO_RANGE, status(emptyList(), t).status)
        assertEquals(StatsCalculator.Status.NO_RANGE, status(listOf(log(day(2026, 8, 10))), t).status)
    }

    // ---- history ----

    @Test fun lastPeriods_weekly_returnsEightPointsWithCounts() {
        val logs = listOf(
            log(day(2026, 8, 10)),                       // current week
            log(day(2026, 8, 3)), log(day(2026, 8, 4)),  // previous week
            log(day(2026, 8, 1)),                        // two weeks back
        )
        val points = StatsCalculator.lastPeriods(logs, FrequencyPeriod.WEEK, today)
        assertEquals(8, points.size)
        assertEquals(1, points.last().count)
        assertEquals(2, points[points.size - 2].count)
        assertEquals(1, points[points.size - 3].count)
        assertEquals(0, points.first().count)
        assertEquals(LocalDate.of(2026, 8, 10).toEpochDay(), points.last().startEpochDay)
        assertEquals(LocalDate.of(2026, 8, 16).toEpochDay(), points.last().endEpochDay)
    }

    @Test fun dailyCounts_returns35DaysWithPerDayCounts() {
        val logs = listOf(log(day(2026, 8, 10)), log(day(2026, 8, 10)), log(day(2026, 8, 9)))
        val counts = StatsCalculator.dailyCounts(logs, today = today)
        assertEquals(35, counts.size)
        assertEquals(2, counts[LocalDate.of(2026, 8, 10)])
        assertEquals(1, counts[LocalDate.of(2026, 8, 9)])
        assertEquals(0, counts[LocalDate.of(2026, 8, 6)])
        assertEquals(LocalDate.of(2026, 7, 7), counts.keys.first()) // oldest day
    }

    @Test fun dayLabel_todayIsHoy() {
        assertEquals("Hoy", StatsCalculator.labelShort(today, today, FrequencyPeriod.DAY, today))
    }
}
