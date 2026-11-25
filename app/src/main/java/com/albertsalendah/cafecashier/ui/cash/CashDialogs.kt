package com.albertsalendah.cafecashier.ui.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.ExpenseCategory
import com.albertsalendah.cafecashier.data.db.ExpensePool
import com.albertsalendah.cafecashier.domain.MAX_EXPENSE_QUANTITY
import com.albertsalendah.cafecashier.domain.buildExpense
import com.albertsalendah.cafecashier.ui.cleanAmountDigits
import com.albertsalendah.cafecashier.ui.components.ThousandsVisualTransformation
import com.albertsalendah.cafecashier.ui.formatRupiah

// Sets the opening cash of the day. [initial] is null when none was entered yet.
// 0 is allowed (an empty drawer); an empty field is not.
@Composable
fun OpeningCashDialog(
    initial: Long?,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial?.toString() ?: "") }
    val amount = text.toLongOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_opening_cash)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = cleanAmountDigits(it) },
                singleLine = true,
                prefix = { Text("Rp ") },
                label = { Text(stringResource(R.string.opening_cash)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                // Shows 200000 as 200.000 while typing; the stored text stays plain digits.
                visualTransformation = ThousandsVisualTransformation,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onSave) }, enabled = amount != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// Adds an expense to the day being shown. Save stays disabled until buildExpense accepts the input,
// which is the same check the ViewModel applies when saving.
@Composable
fun ExpenseDialog(
    categories: List<ExpenseCategory>,
    onSave: (categoryId: Long, name: String, unitAmount: Long, quantity: Int, pool: ExpensePool) -> Unit,
    onDismiss: () -> Unit
) {
    var pickedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("1") }
    var pool by rememberSaveable { mutableStateOf(ExpensePool.DAILY_CASH) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    // The category picked so far, or the first one until the user picks.
    val selected = categories.firstOrNull { it.id == pickedCategoryId } ?: categories.firstOrNull()
    // The day is filled in by the ViewModel when saving; here the object only checks validity and total.
    val built = buildExpense(
        categoryId = selected?.id,
        name = name,
        unitAmount = amountText.toLongOrNull() ?: 0L,
        quantity = quantityText.toIntOrNull() ?: 0,
        pool = pool,
        day = 0
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_expense)) },
        text = {
            if (categories.isEmpty()) {
                Text(stringResource(R.string.expense_no_categories))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category picker: a button showing the current category that opens a menu
                    // (same pattern as the product form).
                    Box {
                        OutlinedButton(
                            onClick = { categoryMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.category_label) + ": " + (selected?.name ?: "-"))
                        }
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        pickedCategoryId = category.id
                                        categoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.expense_description)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = cleanAmountDigits(it) },
                        singleLine = true,
                        prefix = { Text("Rp ") },
                        label = { Text(stringResource(R.string.expense_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandsVisualTransformation,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = cleanAmountDigits(it, maxDigits = MAX_EXPENSE_QUANTITY.toString().length)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.expense_quantity)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pool == ExpensePool.DAILY_CASH,
                            onClick = { pool = ExpensePool.DAILY_CASH },
                            label = { Text(stringResource(R.string.expense_pool_daily)) }
                        )
                        FilterChip(
                            selected = pool == ExpensePool.OPERATIONAL,
                            onClick = { pool = ExpensePool.OPERATIONAL },
                            label = { Text(stringResource(R.string.expense_pool_operational)) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.expense_pool_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (built != null) {
                        Text(
                            text = stringResource(R.string.expense_total, formatRupiah(built.total)),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { built?.let { onSave(it.categoryId, it.name, it.unitAmount, it.quantity, it.pool) } },
                enabled = built != null
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
