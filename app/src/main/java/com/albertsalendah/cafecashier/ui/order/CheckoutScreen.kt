package com.albertsalendah.cafecashier.ui.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.DiscountType
import com.albertsalendah.cafecashier.data.db.OrderDiscount
import com.albertsalendah.cafecashier.domain.quickCashAmounts
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.ThousandsVisualTransformation
import com.albertsalendah.cafecashier.ui.formatRupiah
import com.albertsalendah.cafecashier.ui.theme.StatusColors

// The checkout: pick an order discount (optional), see the final amounts, type the cash received
// (or tap a quick amount), see the change, and confirm. After the payment a confirmation with the
// change is shown; "New order" (or Back) returns to the Home screen.
// [onBack] goes back one step (to the cart); [onDone] goes all the way back to Home.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val viewModel = viewModel<CheckoutViewModel>(factory = appViewModelFactory())
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val amounts by viewModel.amounts.collectAsStateWithLifecycle()
    val discounts by viewModel.discounts.collectAsStateWithLifecycle()
    val selectedDiscountId by viewModel.selectedDiscountId.collectAsStateWithLifecycle()
    val cashText by viewModel.cashText.collectAsStateWithLifecycle()
    val payState by viewModel.payState.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(context.getString(it)) }
    }

    // Nothing to pay for (for example the app was restarted and the order is gone): go back. Checked
    // once when the screen opens, not on every change, because paying clears the order by itself.
    LaunchedEffect(viewModel) {
        if (viewModel.payState.value == PayState.Editing && viewModel.draft.value.activeLines.isEmpty()) {
            onBack()
        }
    }

    val paid = payState as? PayState.Paid
    if (paid != null) {
        // The order is already saved and cleared; Back must not return to the (empty) cart.
        BackHandler(onBack = onDone)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.checkout_title)) }) }
    ) { innerPadding ->
        if (paid != null) {
            PaidContent(
                paid = paid,
                onNewOrder = onDone,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        } else {
            val cash = viewModel.cashAmount(cashText)
            val change = cash - amounts.total
            val canPay = draft.activeLines.isNotEmpty() && change >= 0 && payState == PayState.Editing

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val discountList = discounts
                if (!discountList.isNullOrEmpty()) {
                    DiscountPicker(
                        discounts = discountList,
                        selectedId = selectedDiscountId,
                        onSelect = viewModel::selectDiscount
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmountRow(stringResource(R.string.subtotal), formatRupiah(amounts.subtotal))
                    if (amounts.productDiscountTotal > 0) {
                        AmountRow(
                            stringResource(R.string.product_discounts),
                            "-" + formatRupiah(amounts.productDiscountTotal)
                        )
                    }
                    if (amounts.orderDiscountAmount > 0) {
                        AmountRow(
                            stringResource(R.string.order_discount_label),
                            "-" + formatRupiah(amounts.orderDiscountAmount)
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

                HorizontalDivider()

                OutlinedTextField(
                    value = cashText,
                    onValueChange = viewModel::setCashText,
                    singleLine = true,
                    label = { Text(stringResource(R.string.cash_received)) },
                    prefix = { Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsVisualTransformation,
                    modifier = Modifier.fillMaxWidth()
                )

                QuickCash(
                    total = amounts.total,
                    onPick = { viewModel.setCashText(it.toString()) }
                )

                if (cashText.isNotEmpty() || amounts.total == 0L) {
                    val ok = change >= 0
                    ChangeRow(
                        label = if (ok) {
                            stringResource(R.string.change_label)
                        } else {
                            stringResource(R.string.short_by, formatRupiah(-change))
                        },
                        value = if (ok) formatRupiah(change) else "",
                        color = if (ok) StatusColors.green else MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = viewModel::pay,
                    enabled = canPay,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.confirm_payment)) }
            }
        }
    }
}

// Chips for "None" and every named order discount; one can be chosen, tapping it again clears it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscountPicker(
    discounts: List<OrderDiscount>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.order_discount_label),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.discount_none)) }
            )
            discounts.forEach { discount ->
                val value = when (discount.type) {
                    DiscountType.PERCENT -> stringResource(R.string.order_discount_value_percent, discount.value.toInt())
                    DiscountType.AMOUNT -> formatRupiah(discount.value)
                }
                FilterChip(
                    selected = selectedId == discount.id,
                    onClick = { onSelect(discount.id) },
                    label = { Text(discount.name + " · " + value) }
                )
            }
        }
    }
}

// "Exact" (the total) and the next round notes above it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCash(total: Long, onPick: (Long) -> Unit) {
    val amounts = quickCashAmounts(total)
    if (amounts.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        amounts.forEachIndexed { index, amount ->
            OutlinedButton(onClick = { onPick(amount) }) {
                Text(if (index == 0) stringResource(R.string.cash_exact) else formatRupiah(amount))
            }
        }
    }
}

// The change (green) or how much is still missing (red).
@Composable
private fun ChangeRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

// Shown after the payment: the change to hand back, and the way out.
@Composable
private fun PaidContent(paid: PayState.Paid, onNewOrder: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.payment_received),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.total) + ": " + formatRupiah(paid.total),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.cash_received) + ": " + formatRupiah(paid.cashReceived),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.change_label),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = formatRupiah(paid.change),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = StatusColors.green
            )
            Button(onClick = onNewOrder) { Text(stringResource(R.string.new_order)) }
        }
    }
}
