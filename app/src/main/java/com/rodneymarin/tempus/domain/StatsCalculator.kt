package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.data.LogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure frequency math: calendar period boundaries, counts, projected-pace status.
 * No Android dependencies — unit-testable on the JVM.
 */
object StatsCalculator {

    fun periodStart(day: LocalDate, period: FrequencyPeriod): LocalDate = when (period) {
        FrequencyPeriod.DAY -> day
        FrequencyPeriod.WEEK -> day.with(DayOfWeek.MONDAY)
        FrequencyPeriod.MONTH -> day.withDayOfMonth(1)
    }

    fun periodEnd(day: LocalDate, period: FrequencyPeriod): LocalDate = when (period) {
        FrequencyPeriod.DAY -> day
        FrequencyPeriod.WEEK -> day.with(DayOfWeek.SUNDAY)
        FrequencyPeriod.MONTH -> day.with(TemporalAdjusters.lastDayOfMonth())
    }

    fun countInRange(logs: List<LogEntry>, startEpochDay: Long, endEpochDay: Long): Int =
        logs.count { it.epochDay in startEpochDay..endEpochDay }

    enum class Status { ON_TRACK, LOW, HIGH, NO_RANGE }

    data class TrackStatus(val count: Int, val projected: Int, val status: Status)

    /**
     * Status of the CURRENT calendar period. Compares the projected end-of-period
     * count (count ÷ elapsed fraction) against the range, so early-period counts
     * are judged by pace, not by raw total.
     */
    fun statusFor(
        logs: List<LogEntry>,
        min: Int?,
        max: Int?,
        period: FrequencyPeriod,
        today: LocalDate = LocalDate.now(),
    ): TrackStatus {
        val start = periodStart(today, period).toEpochDay()
        val end = periodEnd(today, period).toEpochDay()
        val count = countInRange(logs, start, end)
        val totalDays = (end - start + 1).toDouble()
        val elapsedDays = ((today.toEpochDay() - start).coerceAtLeast(0) + 1).toDouble()
        val progress = (elapsedDays / totalDays).coerceIn(0.0, 1.0)
        val projected = if (progress > 0) (count / progress).roundToInt() else count
        val status = when {
            min == null && max == null -> Status.NO_RANGE
            max != null && projected > max -> Status.HIGH
            min != null && projected < min -> Status.LOW
            else -> Status.ON_TRACK
        }
        return TrackStatus(count = count, projected = projected, status = status)
    }

    data class PeriodPoint(
        val label: String,
        val startEpochDay: Long,
        val endEpochDay: Long,
        val count: Int,
    )

    /** Last N calendar periods (8; 14 for daily), oldest → newest. */
    fun lastPeriods(
        logs: List<LogEntry>,
        period: FrequencyPeriod,
        today: LocalDate = LocalDate.now(),
    ): List<PeriodPoint> {
        val n = if (period == FrequencyPeriod.DAY) 14 else 8
        val currentStart = periodStart(today, period)
        return (n - 1 downTo 0).map { offset ->
            val anchor = when (period) {
                FrequencyPeriod.DAY -> today.minusDays(offset.toLong())
                FrequencyPeriod.WEEK -> currentStart.minusWeeks(offset.toLong())
                FrequencyPeriod.MONTH -> currentStart.minusMonths(offset.toLong())
            }
            val start = periodStart(anchor, period)
            val end = periodEnd(anchor, period)
            PeriodPoint(
                label = labelShort(start, end, period, today),
                startEpochDay = start.toEpochDay(),
                endEpochDay = end.toEpochDay(),
                count = countInRange(logs, start.toEpochDay(), end.toEpochDay()),
            )
        }
    }

    fun labelShort(
        start: LocalDate,
        end: LocalDate,
        period: FrequencyPeriod,
        today: LocalDate,
        locale: Locale = Locale("es"),
    ): String = when (period) {
        FrequencyPeriod.DAY ->
            if (start == today) "Hoy"
            else start.format(DateTimeFormatter.ofPattern("d MMM", locale))
        FrequencyPeriod.WEEK -> {
            start.format(DateTimeFormatter.ofPattern("d MMM", locale))
        }
        FrequencyPeriod.MONTH ->
            start.month.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase(locale) }
    }

    /** Per-day counts for the last [days] days, oldest → newest. */
    fun dailyCounts(
        logs: List<LogEntry>,
        days: Int = 35,
        today: LocalDate = LocalDate.now(),
    ): Map<LocalDate, Int> =
        (days - 1 downTo 0).associate { offset ->
            val day = today.minusDays(offset.toLong())
            day to countInRange(logs, day.toEpochDay(), day.toEpochDay())
        }
}
