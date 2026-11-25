package com.albertsalendah.cafecashier.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.formatEpochDay

// "‹  Today · 21 Nov 2026  ›" with a "Go to today" button underneath while another day is shown.
// Tapping the date opens a date picker.
@Composable
fun DayBar(
    day: Long,
    today: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onToday: () -> Unit
) {
    val previousDescription = stringResource(R.string.previous_day)
    val nextDescription = stringResource(R.string.next_day)
    val dateText = formatEpochDay(day)
    val label = if (day == today) stringResource(R.string.day_today_with_date, dateText) else dateText

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPrevious,
                modifier = Modifier.semantics { contentDescription = previousDescription }
            ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = onNext,
                modifier = Modifier.semantics { contentDescription = nextDescription }
            ) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        if (day != today) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.go_to_today)) }
        }
    }
}
