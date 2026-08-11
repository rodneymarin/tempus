package com.rodneymarin.tempus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.domain.FrequencyPeriod

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
                    TextButton(
                        onClick = viewModel::save,
                        enabled = ui.name.isNotBlank() && !ui.loading,
                    ) { Text(stringResource(R.string.save)) }
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
            OutlinedTextField(
                value = ui.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = ui.nameError,
                supportingText = if (ui.nameError) {
                    { Text(stringResource(R.string.field_name_error)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.field_emoji),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                EmojiPicker(selected = ui.emoji, onSelect = viewModel::onEmojiChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.section_expected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ui.minText,
                        onValueChange = viewModel::onMinChange,
                        label = { Text(stringResource(R.string.field_min)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ui.rangeError != null,
                        modifier = Modifier.width(110.dp),
                    )
                    OutlinedTextField(
                        value = ui.maxText,
                        onValueChange = viewModel::onMaxChange,
                        label = { Text(stringResource(R.string.field_max)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ui.rangeError != null,
                        modifier = Modifier.width(110.dp),
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FrequencyPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = ui.period == period,
                            onClick = { viewModel.onPeriodChange(period) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = FrequencyPeriod.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (period) {
                                        FrequencyPeriod.DAY -> R.string.period_day
                                        FrequencyPeriod.WEEK -> R.string.period_week
                                        FrequencyPeriod.MONTH -> R.string.period_month
                                    }
                                )
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        ui.rangeError ?: R.string.range_help,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.rangeError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
