package com.rodneymarin.tempus.ui.detail

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.data.LogEntry
import com.rodneymarin.tempus.ui.components.TempusComponents
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LogHistory(
    entries: List<LogEntry>,
    onDelete: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEachIndexed { index, entry ->
            val date = LocalDate.ofEpochDay(entry.epochDay)
            val header = date
                .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es")))
                .replaceFirstChar { it.uppercase() }
            TempusComponents.TempusCard(
                modifier = Modifier.fillMaxWidth(),
                shape = TempusComponents.listItemShape(index, entries.size),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(header, style = MaterialTheme.typography.titleMedium)
                        Text(
                            entry.timeMinutes?.let { minutes ->
                                LocalTime.of(minutes / 60, minutes % 60)
                                    .format(DateTimeFormatter.ofPattern("h:mm a", Locale("es")))
                            } ?: stringResource(R.string.no_time),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}