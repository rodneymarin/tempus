package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** 35 day-squares, oldest → newest; fill intensity = occurrences that day (0/1/2+). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HeatStrip(
    counts: Map<LocalDate, Int>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            (34 downTo 0).forEach { offset ->
                val day = today.minusDays(offset.toLong())
                val count = counts[day] ?: 0
                val color = when {
                    count == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    count == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                )
            }
        }
    }
}
