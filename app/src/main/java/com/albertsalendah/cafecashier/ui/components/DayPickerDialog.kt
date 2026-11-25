package com.albertsalendah.cafecashier.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.epochDayToPickerMillis
import com.albertsalendah.cafecashier.ui.pickerMillisToEpochDay

// The picker reports the chosen date as UTC midnight millis; DateFormat.kt converts it to the
// epoch day the database stores (and back) without any time zone, which avoids off-by-one days.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    initialDay: Long,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = epochDayToPickerMillis(initialDay))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(pickerMillisToEpochDay(it)) } },
                enabled = state.selectedDateMillis != null
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}
