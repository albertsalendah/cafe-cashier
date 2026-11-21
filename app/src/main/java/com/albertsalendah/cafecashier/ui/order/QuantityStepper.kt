package com.albertsalendah.cafecashier.ui.order

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.albertsalendah.cafecashier.R

// "−  2  +": one less, the quantity, one more. Shared by the Order screen and the cart.
// The buttons are text, so no icon is needed; the spoken description comes from the strings.
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decreaseText = stringResource(R.string.decrease_quantity)
    val increaseText = stringResource(R.string.increase_quantity)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onDecrease,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = decreaseText }
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        IconButton(
            onClick = onIncrease,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = increaseText }
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
