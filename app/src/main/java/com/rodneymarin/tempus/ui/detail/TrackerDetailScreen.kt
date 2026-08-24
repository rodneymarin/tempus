package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.data.LogEntry
import com.rodneymarin.tempus.domain.FrequencyPeriod
import com.rodneymarin.tempus.domain.FrequencyRange
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import com.rodneymarin.tempus.ui.components.TempusComponents
import com.rodneymarin.tempus.ui.dashboard.StatusChip
import com.rodneymarin.tempus.ui.theme.StatusAmber
import com.rodneymarin.tempus.ui.theme.StatusGreen
import com.rodneymarin.tempus.ui.theme.StatusRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    trackerId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: TrackerDetailViewModel = viewModel(factory = TrackerDetailViewModel.Factory(trackerId)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val lastLog by viewModel.lastLog.collectAsStateWithLifecycle()
    val exited by viewModel.exited.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSheet by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<LogEntry?>(null) }
    var registerDay by remember { mutableStateOf<LocalDate?>(null) }
    var deleteDay by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDeleteTracker by remember { mutableStateOf(false) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es")) }

    val logRegisteredMsg = stringResource(R.string.log_registered)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(exited) { if (exited) onBack() }

    LaunchedEffect(lastLog) {
        if (lastLog != null) {
            val result = snackbarHostState.showSnackbar(
                message = logRegisteredMsg,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastLog()
            else viewModel.clearLastLog()
        }
    }

    val tracker = ui.tracker
    val today = LocalDate.now()
    val daysWithEvent = ui.dailyCounts.filterValues { it > 0 }.keys
    val todayHasEvent = (ui.dailyCounts[today] ?: 0) > 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tracker?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = tracker != null) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { confirmDeleteTracker = true }, enabled = tracker != null) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (tracker == null) return@Scaffold

        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header with status inline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tracker.emoji, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tracker.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(
                            R.string.expected_summary,
                            "${FrequencyRange.summary(tracker.minFrequency, tracker.maxFrequency)} ${
                                unitLabel(tracker.period)
                            }",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.status?.let { st ->
                    if (st.status != Status.NO_RANGE) {
                        StatusChip(st.status)
                    }
                }
            }

            // Actions - using standardized buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::logToday,
                    enabled = !todayHasEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(if (todayHasEvent) R.string.log_today_done else R.string.log_today),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = { showSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                    ),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.log_another_day), style = MaterialTheme.typography.labelLarge)
                }
            }

            // 30-day calendar
            SectionTitle(stringResource(R.string.heat_title))
            MonthCalendarStrip(
                daysWithEvent = daysWithEvent,
                today = today,
                onDayClick = { day ->
                    if (day in daysWithEvent) deleteDay = day else registerDay = day
                },
            )

            // History
            SectionTitle(stringResource(R.string.history_title))
            if (ui.history.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LogHistory(entries = ui.history, onDelete = { logToDelete = it })
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            RegisterEventSheet(
                onDismiss = { showSheet = false },
                onConfirm = { date, timeMinutes ->
                    viewModel.logOn(date, timeMinutes)
                    showSheet = false
                },
                daysWithEvent = daysWithEvent,
            )
        }
    }

    // Material3 styled dialogs with standardized buttons
    logToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text(stringResource(R.string.delete_log_confirm), style = MaterialTheme.typography.titleLarge) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLog(entry.id)
                        logToDelete = null
                    },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.delete_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    registerDay?.let { day ->
        AlertDialog(
            onDismissRequest = { registerDay = null },
            title = { Text(stringResource(R.string.register_day_confirm, day.format(dayFormatter)), style = MaterialTheme.typography.titleLarge) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logOn(day, null)
                        registerDay = null
                    },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.register)) }
            },
            dismissButton = {
                TextButton(onClick = { registerDay = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deleteDay?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteDay = null },
            title = { Text(stringResource(R.string.delete_day_confirm), style = MaterialTheme.typography.titleLarge) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDay(day)
                        deleteDay = null
                    },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.delete_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDay = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmDeleteTracker) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTracker = false },
            title = { Text(stringResource(R.string.delete_tracker_confirm, tracker?.name ?: ""), style = MaterialTheme.typography.titleLarge) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteTracker() },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.delete_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTracker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun unitLabel(period: FrequencyPeriod): String = stringResource(
    when (period) {
        FrequencyPeriod.WEEK -> R.string.unit_week
        FrequencyPeriod.MONTH -> R.string.unit_month
    }
)