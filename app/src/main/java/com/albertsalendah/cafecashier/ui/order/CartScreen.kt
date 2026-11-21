package com.albertsalendah.cafecashier.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.domain.DraftLine
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.formatRupiah

// The cart: the order's lines (tap one to edit add-ons and note, "-" and "+" to change the
// quantity), optional table and customer name, and the amounts (subtotal, discounts, tax,
// rounding, total) from the sale calculator. "Save bill" keeps the order as an open bill, "Pay"
// goes to the checkout. When the cart shows a saved bill, "Close" leaves it as it was saved and
// "Cancel this bill" cancels it (with a reason); a line removed from a saved bill stays visible,
// struck through, with a "Restore" button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(onBack: () -> Unit, onCheckout: () -> Unit) {
    val viewModel = viewModel<CartViewModel>(factory = appViewModelFactory())
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val amounts by viewModel.amounts.collectAsStateWithLifecycle()

    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelBill by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(context.getString(it)) }
    }
    // The line being edited (its id, so the state survives rotation).
    var editingLineId by rememberSaveable { mutableStateOf<Long?>(null) }

    // An empty cart (last line removed, or the cart cleared) has nothing to show: go back to the
    // Order tab. Only this place goes back, so it can't pop twice.
    val isEmpty = draft.lines.isEmpty()
    val savedBillId = draft.saleId
    val canCheckout = draft.activeLines.isNotEmpty()
    LaunchedEffect(isEmpty) {
        if (isEmpty) onBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (savedBillId != null) billLabel(draft) else stringResource(R.string.cart_title)
                    )
                },
                actions = {
                    if (!isEmpty) {
                        if (savedBillId == null) {
                            TextButton(onClick = { showClearConfirm = true }) {
                                Text(stringResource(R.string.clear))
                            }
                        } else {
                            TextButton(onClick = {
                                if (viewModel.hasUnsavedChanges()) showDiscardConfirm = true else viewModel.clear()
                            }) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isEmpty) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::saveBill,
                            enabled = canCheckout,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.save_bill)) }
                        Button(
                            onClick = onCheckout,
                            enabled = canCheckout,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.pay) + " · " + formatRupiah(amounts.total)) }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.cart_empty)) }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "details") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = orderTypeLabel(draft.orderType),
                            style = MaterialTheme.typography.titleMedium
                        )
                        // A table only makes sense for Dine In.
                        if (draft.orderType == OrderType.DINE_IN) {
                            OutlinedTextField(
                                value = draft.tableNumber,
                                onValueChange = viewModel::setTableNumber,
                                singleLine = true,
                                label = { Text(stringResource(R.string.table_number)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = draft.customerName,
                            onValueChange = viewModel::setCustomerName,
                            singleLine = true,
                            label = { Text(stringResource(R.string.customer_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }

                items(draft.lines, key = { it.id }) { line ->
                    CartLineRow(
                        line = line,
                        onChangeQuantity = { delta -> viewModel.changeQuantity(line.id, delta) },
                        onEdit = { editingLineId = line.id },
                        onRestore = { viewModel.restoreLine(line.id) }
                    )
                    HorizontalDivider()
                }

                item(key = "amounts") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AmountRow(stringResource(R.string.subtotal), formatRupiah(amounts.subtotal))
                        if (amounts.productDiscountTotal > 0) {
                            AmountRow(
                                stringResource(R.string.product_discounts),
                                "-" + formatRupiah(amounts.productDiscountTotal)
                            )
                        }
                        if (amounts.taxAmount > 0) {
                            AmountRow(stringResource(R.string.tax), formatRupiah(amounts.taxAmount))
                        }
                        if (amounts.rounding != 0L) {
                            AmountRow(stringResource(R.string.rounding), formatRupiah(amounts.rounding))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        AmountRow(stringResource(R.string.total), formatRupiah(amounts.total), emphasised = true)
                    }
                    if (savedBillId != null) {
                        TextButton(
                            onClick = { showCancelBill = true },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cancel_bill),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // The editor closes by itself if its line is gone.
    draft.lines.firstOrNull { it.id == editingLineId }?.let { line ->
        val options by remember(line.categoryId) { viewModel.addOnNames(line.categoryId) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        LineEditDialog(
            line = line,
            options = options,
            onSave = { addOns, note ->
                viewModel.updateLine(line.id, addOns, note)
                editingLineId = null
            },
            onRemove = {
                editingLineId = null
                viewModel.removeLine(line.id)
            },
            onDismiss = { editingLineId = null }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.cart_clear_title)) },
            text = { Text(stringResource(R.string.cart_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()   // the empty cart then takes us back (see above)
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    viewModel.clear()   // the bill stays saved as it was; the empty cart takes us back
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }

    if (showCancelBill) {
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancelBill = false },
            title = { Text(stringResource(R.string.cancel_bill_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.cancel_bill_message))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.cancel_reason)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelBill = false
                        viewModel.cancelBill(reason)
                    },
                    enabled = reason.isNotBlank()
                ) {
                    Text(
                        text = stringResource(R.string.cancel_bill),
                        color = if (reason.isNotBlank()) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelBill = false }) {
                    Text(stringResource(R.string.go_back))
                }
            }
        )
    }
}

// One line: name, "2 x Rp 13.000" (with its discount), add-ons and note when there are any, the
// line's gross amount and the quantity stepper. The discount itself is taken off in the amounts below.
// A cancelled line (only possible on a saved bill) is greyed and struck through and has "Restore"
// instead of the stepper; it can't be edited.
@Composable
private fun CartLineRow(
    line: DraftLine,
    onChangeQuantity: (delta: Int) -> Unit,
    onEdit: () -> Unit,
    onRestore: () -> Unit
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val strike = if (line.cancelled) TextDecoration.LineThrough else TextDecoration.None
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (line.cancelled) Modifier else Modifier.clickable(onClick = onEdit))
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                line.productName,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = strike,
                color = if (line.cancelled) secondary else Color.Unspecified
            )
            val price = stringResource(R.string.line_quantity_price, line.quantity, formatRupiah(line.unitPrice))
            Text(
                text = if (line.discountPercent > 0) {
                    price + " · " + stringResource(R.string.discount_off, line.discountPercent)
                } else {
                    price
                },
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = strike,
                color = secondary
            )
            if (line.addOns.isNotEmpty()) {
                Text(
                    text = line.addOns.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = strike,
                    color = secondary
                )
            }
            line.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    textDecoration = strike,
                    color = secondary
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRupiah(line.unitPrice * line.quantity),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = strike,
                color = if (line.cancelled) secondary else Color.Unspecified,
                modifier = Modifier.padding(end = 8.dp)
            )
            if (line.cancelled) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.restore)) }
            } else {
                QuantityStepper(
                    quantity = line.quantity,
                    onDecrease = { onChangeQuantity(-1) },
                    onIncrease = { onChangeQuantity(1) }
                )
            }
        }
    }
}

// Edit one line: tick add-ons (the options of the product's category), write a note, or remove
// the line. [options] are the names offered now; a name chosen earlier that has since been deleted
// from the menu is still shown, so it can be unticked.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineEditDialog(
    line: DraftLine,
    options: List<String>,
    onSave: (addOns: List<String>, note: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by rememberSaveable { mutableStateOf(ArrayList(line.addOns)) }
    var note by rememberSaveable { mutableStateOf(line.note ?: "") }
    val names = options + chosen.filter { it !in options }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.productName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (names.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.line_add_ons),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        names.forEach { name ->
                            FilterChip(
                                selected = name in chosen,
                                onClick = {
                                    chosen = ArrayList(if (name in chosen) chosen - name else chosen + name)
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.line_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.remove_item),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            // Keep the menu's order for the add-ons.
            TextButton(onClick = { onSave(names.filter { it in chosen }, note) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
internal fun AmountRow(label: String, value: String, emphasised: Boolean = false) {
    val style = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    val weight = if (emphasised) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, fontWeight = weight)
        Text(value, style = style, fontWeight = weight)
    }
}
