package com.rodneymarin.tempus.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.ui.theme.ThemeMode
import com.rodneymarin.tempus.ui.theme.ThemePreferenceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTracker: (Long) -> Unit,
    onCreateTracker: () -> Unit,
    themePrefs: ThemePreferenceManager,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val lastLog by viewModel.lastLog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val logRegistered = stringResource(R.string.log_registered)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(lastLog) {
        if (lastLog != null) {
            val result = snackbarHostState.showSnackbar(
                message = logRegistered,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.undoLastLog()
            } else {
                viewModel.clearLastLog()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = { ThemeMenu(themePrefs) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTracker,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_tracker))
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(onCreateTracker, Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(rows, key = { it.tracker.id }) { row ->
                    TrackerCard(
                        row = row,
                        onOpen = { onOpenTracker(row.tracker.id) },
                        onLogToday = { viewModel.logToday(row.tracker.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeMenu(themePrefs: ThemePreferenceManager) {
    val mode by themePrefs.mode.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val lightLabel = stringResource(R.string.theme_light)
    val darkLabel = stringResource(R.string.theme_dark)
    val systemLabel = stringResource(R.string.theme_system)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { entry ->
                val label = when (entry) {
                    ThemeMode.LIGHT -> lightLabel
                    ThemeMode.DARK -> darkLabel
                    ThemeMode.SYSTEM -> systemLabel
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (mode == entry) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        themePrefs.setMode(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Text("⏳", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_state_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_state_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreate) { Text(stringResource(R.string.empty_state_action)) }
    }
}
