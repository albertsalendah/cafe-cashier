package com.albertsalendah.cafecashier.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.albertsalendah.cafecashier.R

// One dialog for adding or renaming something that only has a name (categories, add-on options).
// [extra] is placed under the text field, for a "Hide from menu" or "Delete" button and its help text.
@Composable
fun NameDialog(
    title: String,
    label: String,
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(label) }
                )
                extra?.invoke()
            }
        },
        confirmButton = {
            // Blank names are rejected by keeping Save disabled.
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
