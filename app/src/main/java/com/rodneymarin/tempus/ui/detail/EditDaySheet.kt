package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.ui.components.TempusComponents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sheet de edición de un día con evento: solo permite editar el comentario.
 * Acciones: Aceptar (guarda el comentario) y Eliminar (borra el registro).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDaySheet(
    date: LocalDate,
    initialComment: String?,
    onSave: (String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var comment by remember { mutableStateOf(initialComment.orEmpty()) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    }
    val header = date.format(dateFormatter).replaceFirstChar { it.uppercase() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.edit_day_title, header),
                style = MaterialTheme.typography.titleMedium,
            )

            TempusComponents.TempusTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = stringResource(R.string.comment_placeholder),
                singleLine = false,
                minLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TempusComponents.ActionButton(
                    onClick = onDelete,
                    label = stringResource(R.string.delete),
                    primary = false,
                    modifier = Modifier.weight(1f).height(48.dp),
                )
                TempusComponents.ActionButton(
                    onClick = {
                        onSave(comment.trim().takeIf { it.isNotEmpty() })
                    },
                    label = stringResource(R.string.accept),
                    primary = true,
                    modifier = Modifier.weight(1f).height(48.dp),
                )
            }
        }
    }
}
