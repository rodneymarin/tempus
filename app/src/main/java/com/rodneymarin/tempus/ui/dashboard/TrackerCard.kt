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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.domain.FrequencyPeriod

@Composable
fun TrackerCard(
    row: TrackerRow,
    onOpen: () -> Unit,
    onLogToday: () -> Unit,
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
                    periodSummary(row.tracker, row.countInPeriod, row.expectedSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(row.status)
                IconButton(onClick = onLogToday, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.log_today),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun periodSummary(tracker: Tracker, count: Int, expected: String): String {
    val prefix = when (tracker.period) {
        FrequencyPeriod.DAY -> stringResource(R.string.period_prefix_day)
        FrequencyPeriod.WEEK -> stringResource(R.string.period_prefix_week)
        FrequencyPeriod.MONTH -> stringResource(R.string.period_prefix_month)
    }
    val hasRange = tracker.minFrequency != null || tracker.maxFrequency != null
    return if (hasRange) {
        stringResource(R.string.this_period_count_range, prefix, count, expected)
    } else {
        stringResource(R.string.this_period_count, prefix, count)
    }
}
