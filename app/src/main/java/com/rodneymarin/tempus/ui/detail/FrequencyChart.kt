package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodneymarin.tempus.domain.StatsCalculator.PeriodPoint

/**
 * Rounded bars = actual count per period; shaded band = expected [min, max]
 * with dashed boundary lines. Labels are staggered (every 2nd) to prevent overlap.
 * Zero-dependency by design (no chart library).
 */
@Composable
fun FrequencyChart(
    points: List<PeriodPoint>,
    min: Int?,
    max: Int?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val rangeColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.isEmpty()) return@Canvas

        val maxVal = maxOf(points.maxOf { it.count }, max ?: 0, min ?: 0, 1)
        val leftPad = 4.dp.toPx()
        val rightPad = 4.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 30.dp.toPx()
        val chartTop = topPad
        val chartBottom = size.height - bottomPad
        val chartH = chartBottom - chartTop
        val chartW = size.width - leftPad - rightPad
        val slot = chartW / points.size
        val barW = minOf(slot * 0.55f, 26.dp.toPx())

        fun yFor(v: Int): Float = chartBottom - (v.toFloat() / maxVal) * chartH

        // gridlines (0, ¼, ½, ¾, max)
        repeat(5) { i ->
            val v = maxVal * i / 4
            drawLine(
                gridColor, Offset(leftPad, yFor(v)),
                Offset(size.width - rightPad, yFor(v)), 1.dp.toPx(),
            )
        }

        // expected range: translucent fill + dashed boundary lines
        if (min != null && max != null) {
            val yMin = yFor(min).coerceIn(chartTop, chartBottom)
            val yMax = yFor(max).coerceIn(chartTop, chartBottom)
            val topY = kotlin.math.min(yMin, yMax)
            val bandH = kotlin.math.abs(yMax - yMin)
            drawRect(
                color = rangeColor.copy(alpha = 0.08f),
                topLeft = Offset(leftPad, topY),
                size = Size(chartW, bandH),
            )
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        if (min != null) {
            val y = yFor(min).coerceIn(chartTop, chartBottom)
            drawLine(rangeColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 2.dp.toPx(), pathEffect = dash)
        }
        if (max != null) {
            val y = yFor(max).coerceIn(chartTop, chartBottom)
            drawLine(rangeColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 2.dp.toPx(), pathEffect = dash)
        }

        // bars + staggered x labels
        val showEvery = if (points.size <= 5) 1 else 2
        points.forEachIndexed { i, p ->
            val cx = leftPad + slot * i + slot / 2
            val barTop = yFor(p.count)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barW / 2, barTop),
                size = Size(barW, (chartBottom - barTop).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            // label only every Nth point to prevent overlap
            if (i % showEvery == 0) {
                val layout = textMeasurer.measure(p.label, labelStyle)
                val x = cx - layout.size.width / 2f
                drawText(layout, topLeft = Offset(x, chartBottom + 6.dp.toPx()))
            }
        }
    }
}
