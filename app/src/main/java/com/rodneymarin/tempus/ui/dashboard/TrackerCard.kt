package com.rodneymarin.tempus.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun TrackerCard(
    row: TrackerRow,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.tracker.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.tracker.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    lastEventSummary(row.lastEventDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(row.status)
            }
        }
    }
}

@Composable
private fun lastEventSummary(lastEventDay: LocalDate?): String {
    val info = lastEventInfo(lastEventDay, LocalDate.now())
    return when (info.kind) {
        LastEventKind.NONE -> stringResource(R.string.last_event_none)
        LastEventKind.TODAY -> stringResource(R.string.last_event_today)
        LastEventKind.DAYS -> pluralStringResource(R.plurals.last_event_days, info.amount, info.amount)
        LastEventKind.WEEKS -> pluralStringResource(R.plurals.last_event_weeks, info.amount, info.amount)
    }
}

internal enum class LastEventKind { NONE, TODAY, DAYS, WEEKS }

internal data class LastEventInfo(val kind: LastEventKind, val amount: Int)

/** Clasifica la antigüedad del último evento: <7 días se expresa en días, >=7 en semanas. */
internal fun lastEventInfo(lastEventDay: LocalDate?, today: LocalDate): LastEventInfo {
    if (lastEventDay == null) return LastEventInfo(LastEventKind.NONE, 0)
    val days = ChronoUnit.DAYS.between(lastEventDay, today)
    return when {
        days <= 0 -> LastEventInfo(LastEventKind.TODAY, 0)
        days < 7 -> LastEventInfo(LastEventKind.DAYS, days.toInt())
        else -> LastEventInfo(LastEventKind.WEEKS, (days / 7).toInt())
    }
}
