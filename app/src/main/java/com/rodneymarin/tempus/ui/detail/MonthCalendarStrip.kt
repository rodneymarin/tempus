package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val localeEs = Locale("es")

@Composable
fun MonthCalendarStrip(
    daysWithEvent: Set<LocalDate>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val startDate = today.minusDays(29)
    val endDate = today

    // Find all months that appear in the 30-day range.
    val months = mutableListOf<YearMonth>()
    var cursor = startDate
    while (!cursor.isAfter(endDate)) {
        val ym = YearMonth.from(cursor)
        if (ym !in months) months.add(ym)
        cursor = cursor.plusDays(1)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        months.forEach { yearMonth ->
            // The range of days from this month that fall within [startDate, endDate].
            val monthStart = maxOf(yearMonth.atDay(1), startDate)
            val monthEnd = minOf(yearMonth.atEndOfMonth(), endDate)

            // Sunday-first: 1=Mon … 7=Sun → offset = value % 7 (Sun=0)
            val daysBefore = (monthStart.dayOfWeek.value % 7)
            val gridStart = monthStart.minusDays(daysBefore.toLong())

            // Saturday-aligned end: fill the row so every week is complete.
            val daysAfter = (6 - monthEnd.dayOfWeek.value % 7)
            val gridEnd = monthEnd.plusDays(daysAfter.toLong())

            val totalDays = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1
            val weeks = totalDays / 7

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = yearMonth.month.getDisplayName(TextStyle.FULL, localeEs)
                        .replaceFirstChar { c -> c.uppercase(localeEs) },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(weeks) { weekIndex ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            repeat(7) { dayIndex ->
                                val cellDate = gridStart.plusDays((weekIndex * 7 + dayIndex).toLong())
                                val isInRange = !cellDate.isBefore(monthStart) && !cellDate.isAfter(monthEnd)
                                val isToday = cellDate == today
                                val hasEvent = isInRange && (cellDate in daysWithEvent)

                                val bgColor = when {
                                    !isInRange -> Color.Transparent
                                    hasEvent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bgColor)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    if (isInRange) {
                                        Text(
                                            text = cellDate.dayOfWeek.getDisplayName(TextStyle.SHORT, localeEs)
                                                .replaceFirstChar { c -> c.uppercase(localeEs) },
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(start = 2.dp, top = 0.dp),
                                           	style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = cellDate.dayOfMonth.toString(),
                                            modifier = Modifier.offset(y = 4.dp),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (isToday) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                    .align(Alignment.TopEnd),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}