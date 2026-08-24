package com.rodneymarin.tempus.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import com.rodneymarin.tempus.ui.theme.StatusAmber
import com.rodneymarin.tempus.ui.theme.StatusGreen
import com.rodneymarin.tempus.ui.theme.StatusRed

@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val labelRes: Int
    val color: Color
    when (status) {
        Status.ON_TRACK -> {
            labelRes = R.string.status_on_track
            color = StatusGreen
        }
        Status.LOW -> {
            labelRes = R.string.status_low
            color = StatusAmber
        }
        Status.HIGH -> {
            labelRes = R.string.status_high
            color = StatusRed
        }
        Status.NO_RANGE -> {
            labelRes = R.string.status_no_range
            color = MaterialTheme.colorScheme.outline
        }
    }

    Surface(
        shape = RoundedCornerShape(50), // Pill perfecto, consistente con el sistema
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        }
    }
}