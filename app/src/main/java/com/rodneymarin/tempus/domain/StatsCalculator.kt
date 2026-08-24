package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.data.LogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Pure frequency math: calendar period boundaries, counts, projected-pace status.
 * No Android dependencies — unit-testable on the JVM.
 */
object StatsCalculator {

    fun periodStart(day: LocalDate, period: FrequencyPeriod): LocalDate = when (period) {
        FrequencyPeriod.WEEK -> day.with(DayOfWeek.MONDAY)
        FrequencyPeriod.MONTH -> day.withDayOfMonth(1)
    }

    fun periodEnd(day: LocalDate, period: FrequencyPeriod): LocalDate = when (period) {
        FrequencyPeriod.WEEK -> day.with(DayOfWeek.SUNDAY)
        FrequencyPeriod.MONTH -> day.with(TemporalAdjusters.lastDayOfMonth())
    }

    fun countInRange(logs: List<LogEntry>, startEpochDay: Long, endEpochDay: Long): Int =
        logs.count { it.epochDay in startEpochDay..endEpochDay }

    enum class Status { ON_TRACK, LOW, HIGH, NO_RANGE }

    data class TrackStatus(val count: Int, val status: Status)

    /** Longitud de la ventana móvil según el periodo configurado. */
    fun windowDays(period: FrequencyPeriod): Long = when (period) {
        FrequencyPeriod.WEEK -> 7L
        FrequencyPeriod.MONTH -> 30L
    }

    /**
     * Status basado en una ventana móvil de los últimos N días (hoy incluido),
     * donde N es la longitud del periodo (7 para semana, 30 para mes).
     * Compara el conteo real de la ventana contra el rango esperado:
     * count > max → HIGH, count < min → LOW, en rango → ON_TRACK.
     * Sin proyecciones: un conteo bajo al inicio de la semana es LOW,
     * no se extrapola linealmente.
     */
    fun statusFor(
        logs: List<LogEntry>,
        min: Int?,
        max: Int?,
        period: FrequencyPeriod,
        today: LocalDate = LocalDate.now(),
    ): TrackStatus {
        val start = today.toEpochDay() - (windowDays(period) - 1)
        val end = today.toEpochDay()
        val count = countInRange(logs, start, end)
        val status = when {
            min == null && max == null -> Status.NO_RANGE
            max != null && count > max -> Status.HIGH
            min != null && count < min -> Status.LOW
            else -> Status.ON_TRACK
        }
        return TrackStatus(count = count, status = status)
    }

    data class PeriodPoint(
        val label: String,
        val startEpochDay: Long,
        val endEpochDay: Long,
        val count: Int,
    )

    /** Last 8 calendar periods, oldest → newest. */
    fun lastPeriods(
        logs: List<LogEntry>,
        period: FrequencyPeriod,
        today: LocalDate = LocalDate.now(),
    ): List<PeriodPoint> {
        val n = 8
        val currentStart = periodStart(today, period)
        return (n - 1 downTo 0).map { offset ->
            val anchor = when (period) {
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
