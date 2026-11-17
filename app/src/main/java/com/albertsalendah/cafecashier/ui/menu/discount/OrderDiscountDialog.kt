package com.albertsalendah.cafecashier.ui.menu.discount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.DiscountType
import com.albertsalendah.cafecashier.ui.components.ThousandsVisualTransformation

// Add or edit an order discount: a name, a type (percent or rupiah amount) and a value.
// Same structure as NameDialog; [extra] sits under the fields, for the Delete button when editing.
@Composable
fun OrderDiscountDialog(
    title: String,
    initialName: String,
    initialType: DiscountType,
    initialValue: Long?,
    onSave: (name: String, type: DiscountType, value: Long) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var isPercent by rememberSaveable { mutableStateOf(initialType == DiscountType.PERCENT) }
    var valueText by rememberSaveable { mutableStateOf(initialValue?.toString() ?: "") }

    val value = valueText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.order_discount_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isPercent,
                        onClick = {
                            isPercent = true
                            valueText = cleanValueText(valueText, percent = true)
                        },
                        label = { Text(stringResource(R.string.order_discount_type_percent)) }
                    )
                    FilterChip(
                        selected = !isPercent,
                        onClick = {
                            isPercent = false
                            valueText = cleanValueText(valueText, percent = false)
                        },
                        label = { Text(stringResource(R.string.order_discount_type_amount)) }
                    )
                }

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = cleanValueText(it, isPercent) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.order_discount_value)) },
                    prefix = if (isPercent) null else ({ Text("Rp ") }),
                    suffix = if (isPercent) ({ Text("%") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    // Thousand separators only for a rupiah amount; the stored text stays plain digits.
                    visualTransformation = if (isPercent) VisualTransformation.None else ThousandsVisualTransformation,
                    modifier = Modifier.fillMaxWidth()
                )

                extra?.invoke()
            }
        },
        confirmButton = {
            // Save stays disabled for a blank name or a value of 0.
            TextButton(
                onClick = { onSave(name, if (isPercent) DiscountType.PERCENT else DiscountType.AMOUNT, value) },
                enabled = name.isNotBlank() && value > 0L
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// Digits only, no leading zeros. A percent is limited to 100 (so 3 digits); an amount to 12 digits.
private fun cleanValueText(raw: String, percent: Boolean): String {
    val digits = raw.filter { it.isDigit() }.trimStart('0').take(if (percent) 3 else 12)
    return if (percent && (digits.toIntOrNull() ?: 0) > 100) "100" else digits
}
