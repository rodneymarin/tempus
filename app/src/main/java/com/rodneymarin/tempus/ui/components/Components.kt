package com.rodneymarin.tempus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.domain.FrequencyPeriod
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import com.rodneymarin.tempus.ui.theme.StatusAmber
import com.rodneymarin.tempus.ui.theme.StatusGreen
import com.rodneymarin.tempus.ui.theme.StatusRed

/**
 * Componentes UI armonizados de Tempus, estilo Google:
 * - Todos los controles son pills perfectos (RoundedCornerShape(50)).
 * - Inputs y dropdowns RELLENOS sin bordes: fondo gris claro (claro) /
 *   gris oscuro (oscuro), texto sobre el fondo, solo placeholder (sin label).
 * - Botones de selección tipo pill relleno (sin bordes ni segmentación).
 */
object TempusComponents {

    /** Pill perfecto: radio igual a la mitad de la altura, en cualquier tamaño. */
    val PillShape = RoundedCornerShape(50)

    /** Superficie adaptada: blanco puro en tema claro (el fondo de la app es gris claro),
     *  gris de superficie en tema oscuro. La usan inputs, dropdowns y cards.
     *  Nota: NO usamos colorScheme.surface porque con Material You no es blanco puro. */
    @Composable
    fun adaptiveSurface(): Color {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        return if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest
        else Color.White
    }

    @Composable
    private fun filledFieldColors() = TextFieldDefaults.colors(
        focusedContainerColor = adaptiveSurface(),
        unfocusedContainerColor = adaptiveSurface(),
        disabledContainerColor = adaptiveSurface(),
        errorContainerColor = adaptiveSurface(),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    // ============ BOTONES (pills perfectos) ============

    /**
     * Botón primario relleno. NO fuerza ancho ni alto: el caller decide
     * (compacto en top bars, expandido con Modifier.fillMaxWidth().height(56.dp)).
     */
    @Composable
    fun PrimaryButton(
        onClick: () -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: (@Composable () -> Unit)? = null,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.size(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }

    /** Botón secundario relleno con container color. */
    @Composable
    fun SecondaryButton(
        onClick: () -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }

    // ============ CARD UNIFICADO PARA LISTAS ============

    /**
     * Forma de un item dentro de una lista agrupada estilo M3: el grupo
     * completo tiene esquinas exteriores muy redondeadas (20dp) y las
     * esquinas internas (que colindan con otros items) casi rectas (4dp);
     * el primero/último redondean solo sus esquinas del extremo.
     */
    @Composable
    fun listItemShape(index: Int, count: Int): Shape {
        val outer = 20.dp
        val inner = 4.dp
        return when {
            count <= 1 -> RoundedCornerShape(outer)
            index == 0 -> RoundedCornerShape(
                topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner,
            )
            index == count - 1 -> RoundedCornerShape(
                topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer,
            )
            else -> RoundedCornerShape(inner)
        }
    }

    /**
     * Card único para todos los items de lista (registros del dashboard,
     * historial de eventos, etc.). Diseño: blanco en tema claro / primary
     * al 10% en dark, sin sombra. Dentro de una lista agrupada pasar
     * `shape` desde listItemShape(index, count).
     */
    @Composable
    fun TempusCard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        shape: Shape? = null,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    ) {
        val cardShape = shape ?: RoundedCornerShape(24.dp)
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val containerColor =
            if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else adaptiveSurface()
        val colors = CardDefaults.cardColors(containerColor = containerColor)
        val noElevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        )
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = cardShape,
                colors = colors,
                elevation = noElevation,
                content = content,
            )
        } else {
            Card(
                modifier = modifier,
                shape = cardShape,
                colors = colors,
                elevation = noElevation,
                content = content,
            )
        }
    }

    // ============ SHEET DE CONFIRMACIÓN ============

    /**
     * Sheet inferior estilo M3 para confirmaciones: título + única acción
     * afirmativa (sin botón de cancelar). El usuario cierra haciendo tap
     * fuera del sheet o deslizando hacia abajo.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ConfirmSheet(
        title: String,
        actionLabel: String,
        actionContainerColor: Color,
        actionContentColor: Color,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = actionContainerColor,
                        contentColor = actionContentColor,
                    ),
                    shape = PillShape,
                ) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    // ============ INPUTS (rellenos, sin borde, solo placeholder) ============

    @Composable
    fun TempusTextField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier = Modifier,
        singleLine: Boolean = true,
        isError: Boolean = false,
        supportingText: String? = null,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = singleLine,
            isError = isError,
            supportingText = if (supportingText != null) {
                { Text(supportingText) }
            } else null,
            shape = PillShape,
            colors = filledFieldColors(),
        )
    }

    // ============ DROPDOWNS DE RANGO (1..max según periodo) ============

    /**
     * Selector tipo dropdown estilo Google: campo relleno sin borde,
     * solo placeholder, opciones predefinidas 1..max.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RangeDropdown(
        placeholder: String,
        selected: Int?,
        maxValue: Int,
        onSelected: (Int?) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = modifier,
        ) {
            TextField(
                value = selected?.toString() ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                shape = PillShape,
                colors = filledFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                (1..maxValue).forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value.toString()) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    // ============ SELECTOR DE PERIODO (Segmented Button M3) ============

    /**
     * Segmented Button según lineamientos Material 3: contenedor pill con
     * borde outline, divisores entre segmentos, segmento seleccionado con
     * relleno secondaryContainer e ícono de check.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PeriodSelector(
        selected: FrequencyPeriod,
        onSelect: (FrequencyPeriod) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            FrequencyPeriod.entries.forEachIndexed { index, period ->
                val isSelected = selected == period
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onSelect(period) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = FrequencyPeriod.entries.size,
                    ),
                    icon = {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    label = {
                        Text(
                            stringResource(
                                when (period) {
                                    FrequencyPeriod.WEEK -> R.string.period_week
                                    FrequencyPeriod.MONTH -> R.string.period_month
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }

    // ============ CHIP DE ESTADO (pill perfecto) ============

    @Composable
    fun StatusChip(status: Status, modifier: Modifier = Modifier) {
        val labelRes: Int
        val color: Color
        when (status) {
            Status.ON_TRACK -> { labelRes = R.string.status_on_track; color = StatusGreen }
            Status.LOW -> { labelRes = R.string.status_low; color = StatusAmber }
            Status.HIGH -> { labelRes = R.string.status_high; color = StatusRed }
            Status.NO_RANGE -> { labelRes = R.string.status_no_range; color = MaterialTheme.colorScheme.outline }
        }
        Surface(
            shape = PillShape,
            color = color.copy(alpha = 0.14f),
            contentColor = color,
            modifier = modifier,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}