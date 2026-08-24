package com.rodneymarin.tempus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.domain.FrequencyPeriod
import com.rodneymarin.tempus.ui.components.TempusComponents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEditorScreen(
    trackerId: Long?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TrackerEditorViewModel = viewModel(factory = TrackerEditorViewModel.Factory(trackerId)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()

    LaunchedEffect(done) { if (done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (trackerId == null) R.string.new_tracker else R.string.edit_tracker)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    // Botón compacto de pill, proporcional al resto del top bar.
                    TempusComponents.PrimaryButton(
                        onClick = viewModel::save,
                        label = stringResource(R.string.save),
                        enabled = ui.name.isNotBlank() && !ui.loading,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            TempusComponents.TempusTextField(
                value = ui.name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.field_name),
                isError = ui.nameError,
                supportingText = if (ui.nameError) stringResource(R.string.field_name_error) else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.field_emoji),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                EmojiPicker(selected = ui.emoji, onSelect = viewModel::onEmojiChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.section_expected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                TempusComponents.PeriodSelector(
                    selected = ui.period,
                    onSelect = viewModel::onPeriodChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Rango esperado como dropdowns con opciones predefinidas:
                // por semana -> 1..7, por mes -> 1..31.
                val maxValue = when (ui.period) {
                    FrequencyPeriod.WEEK -> 7
                    FrequencyPeriod.MONTH -> 31
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TempusComponents.RangeDropdown(
                        placeholder = stringResource(R.string.field_min),
                        selected = ui.minFrequency,
                        maxValue = maxValue,
                        onSelected = viewModel::onMinChange,
                        modifier = Modifier.weight(1f),
                    )
                    TempusComponents.RangeDropdown(
                        placeholder = stringResource(R.string.field_max),
                        selected = ui.maxFrequency,
                        maxValue = maxValue,
                        onSelected = viewModel::onMaxChange,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = stringResource(ui.rangeError ?: R.string.range_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ui.rangeError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}