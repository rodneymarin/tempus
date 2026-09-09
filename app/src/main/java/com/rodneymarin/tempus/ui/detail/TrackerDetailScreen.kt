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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var editDay by remember { mutableStateOf<LocalDate?>(null) }
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
    val daysWithComment = ui.history
        .filter { !it.comment.isNullOrBlank() }
        .map { LocalDate.ofEpochDay(it.epochDay) }
        .toSet()
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

            // Actions - par de botones lado a lado con el mismo componente
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TempusComponents.ActionButton(
                    onClick = viewModel::logToday,
                    label = stringResource(
                        if (todayHasEvent) R.string.action_today_done else R.string.action_today,
                    ),
                    primary = true,
                    enabled = !todayHasEvent,
                    icon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                )
                TempusComponents.ActionButton(
                    onClick = { showSheet = true },
                    label = stringResource(R.string.log_another_day),
                    primary = false,
                    modifier = Modifier.weight(1f).height(48.dp),
                )
            }

            // 30-day calendar
            SectionTitle(stringResource(R.string.heat_title))
            MonthCalendarStrip(
                daysWithEvent = daysWithEvent,
                today = today,
                daysWithComment = daysWithComment,
                onDayClick = { day ->
                    if (day in daysWithEvent) editDay = day else registerDay = day
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
                onConfirm = { date, timeMinutes, comment ->
                    viewModel.logOn(date, timeMinutes, comment)
                    showSheet = false
                },
                daysWithEvent = daysWithEvent,
            )
        }
    }

    // Confirmaciones como bottom sheets M3 (solo acción afirmativa;
    // se cierran con tap fuera o deslizando hacia abajo)
    logToDelete?.let { entry ->
        TempusComponents.ConfirmSheet(
            title = stringResource(R.string.delete_log_confirm),
            actionLabel = stringResource(R.string.delete_confirm_action),
            actionContainerColor = MaterialTheme.colorScheme.errorContainer,
            actionContentColor = MaterialTheme.colorScheme.onErrorContainer,
            onConfirm = {
                viewModel.deleteLog(entry.id)
                logToDelete = null
            },
            onDismiss = { logToDelete = null },
        )
    }

    registerDay?.let { day ->
        TempusComponents.ConfirmSheet(
            title = stringResource(R.string.register_day_confirm, day.format(dayFormatter)),
            actionLabel = stringResource(R.string.register),
            actionContainerColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.onPrimary,
            onConfirm = {
                viewModel.logOn(day, null)
                registerDay = null
            },
            onDismiss = { registerDay = null },
        )
    }

    editDay?.let { day ->
        EditDaySheet(
            date = day,
            initialComment = ui.history
                .firstOrNull { it.epochDay == day.toEpochDay() }
                ?.comment,
            onSave = { comment ->
                viewModel.updateComment(day, comment)
                editDay = null
            },
            onDelete = {
                viewModel.deleteDay(day)
                editDay = null
            },
            onDismiss = { editDay = null },
        )
    }

    if (confirmDeleteTracker) {
        TempusComponents.ConfirmSheet(
            title = stringResource(R.string.delete_tracker_confirm, tracker?.name ?: ""),
            actionLabel = stringResource(R.string.delete_confirm_action),
            actionContainerColor = MaterialTheme.colorScheme.errorContainer,
            actionContentColor = MaterialTheme.colorScheme.onErrorContainer,
            onConfirm = { viewModel.deleteTracker() },
            onDismiss = { confirmDeleteTracker = false },
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