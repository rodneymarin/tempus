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

    // ---- status (ventana móvil de los últimos N días) ----

    private fun tracker(min: Int?, max: Int?, period: FrequencyPeriod = FrequencyPeriod.WEEK) =
        Triple(min, max, period)

    private fun status(logs: List<LogEntry>, t: Triple<Int?, Int?, FrequencyPeriod>): StatsCalculator.TrackStatus =
        StatsCalculator.statusFor(logs, t.first, t.second, t.third, today)

    @Test fun oneLogToday_min3Max5_isLow() {
        // 1 evento en los últimos 7 días < mínimo 3 → LOW (caso reportado por el usuario)
        val t = tracker(3, 4)
        val s = status(listOf(log(day(2026, 8, 10))), t)
        assertEquals(StatsCalculator.Status.LOW, s.status)
        assertEquals(1, s.count)
    }

    @Test fun zeroLogs_isLow() {
        val t = tracker(3, 5)
        assertEquals(StatsCalculator.Status.LOW, status(emptyList(), t).status)
    }

    @Test fun fourLogsInLastSevenDays_min3Max5_isOnTrack() {
        // 4 eventos dentro de la ventana de 7 días ∈ [3,5] → ON_TRACK
        val t = tracker(3, 5)
        val s = status(
            listOf(
                log(day(2026, 8, 4)), log(day(2026, 8, 6)),
                log(day(2026, 8, 8)), log(day(2026, 8, 10)),
            ),
            t,
        )
        assertEquals(StatsCalculator.Status.ON_TRACK, s.status)
        assertEquals(4, s.count)
    }

    @Test fun sixLogsInLastSevenDays_max5_isHigh() {
        val t = tracker(3, 5)
        val s = status(
            listOf(
                log(day(2026, 8, 5)), log(day(2026, 8, 6)), log(day(2026, 8, 7)),
                log(day(2026, 8, 8)), log(day(2026, 8, 9)), log(day(2026, 8, 10)),
            ),
            t,
        )
        assertEquals(StatsCalculator.Status.HIGH, s.status)
    }

    @Test fun logsOutsideWindow_doNotCount() {
        // Un log hace 8 días (fuera de ventana) + ninguno reciente → LOW
        val t = tracker(3, 5)
        val s = status(listOf(log(day(2026, 8, 2))), t)
        assertEquals(0, s.count)
        assertEquals(StatsCalculator.Status.LOW, s.status)
    }

    @Test fun boundary_exactlyMin_isOnTrack() {
        val t = tracker(3, 5)
        val s = status(
            listOf(log(day(2026, 8, 8)), log(day(2026, 8, 9)), log(day(2026, 8, 10))), t,
        )
        assertEquals(StatsCalculator.Status.ON_TRACK, s.status)
    }

    @Test fun boundary_exactlyMax_isOnTrack() {
        val t = tracker(3, 5)
        val s = status(
            listOf(
                log(day(2026, 8, 7)), log(day(2026, 8, 8)),
                log(day(2026, 8, 9)), log(day(2026, 8, 10)),
            ),
            t,
        )
        assertEquals(StatsCalculator.Status.ON_TRACK, s.status)
    }

    @Test fun monthlyWindow_countsLastThirtyDays() {
        val t = tracker(3, 5, FrequencyPeriod.MONTH)
        // Logs a 10 y 20 días de hoy (2026-08-10): dentro de ventana de 30 días
        val s = status(
            listOf(log(day(2026, 7, 21)), log(day(2026, 7, 31))), t,
        )
        assertEquals(2, s.count)
        assertEquals(StatsCalculator.Status.LOW, s.status)
    }

    @Test fun maxOnly_threeLogs_isHigh() {
        val t = tracker(null, 2)
        val s = status(
            listOf(log(day(2026, 8, 9)), log(day(2026, 8, 10)), log(day(2026, 8, 10))), t,
        )
        assertEquals(StatsCalculator.Status.HIGH, s.status)
    }

    @Test fun minOnly_oneLogToday_isLow() {
        val t = tracker(3, null)
        val s = status(listOf(log(day(2026, 8, 10))), t)
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
}
