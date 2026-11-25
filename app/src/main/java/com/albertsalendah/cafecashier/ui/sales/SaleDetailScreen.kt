package com.albertsalendah.cafecashier.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleItem
import com.albertsalendah.cafecashier.data.db.SaleItemStatus
import com.albertsalendah.cafecashier.domain.HistoryStatus
import com.albertsalendah.cafecashier.domain.cleanVoidReason
import com.albertsalendah.cafecashier.domain.historyStatus
import com.albertsalendah.cafecashier.domain.lineNetAmount
import com.albertsalendah.cafecashier.ui.formatBillTime
import com.albertsalendah.cafecashier.ui.formatRupiah
import com.albertsalendah.cafecashier.ui.order.orderTypeLabel
import java.time.LocalDate

// One sale in full: its lines, every amount that makes up the total, the cash received and change,
// and (for a paid sale) a Void button. A voided sale shows when and why it was voided.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(saleId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as CafeCashierApp
    val viewModel = viewModel<SaleDetailViewModel>(
        factory = viewModelFactory {
            initializer {
                SaleDetailViewModel(
                    saleId = saleId,
                    saleDao = app.database.saleDao(),
                    saleItemDao = app.database.saleItemDao(),
                    saleWriteDao = app.database.saleWriteDao()
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiding by viewModel.voiding.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    var showVoid by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.void_failed)
    LaunchedEffect(failed) {
        if (failed) {
            snackbarHostState.showSnackbar(failedText)
            viewModel.failureShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state?.sale?.let { stringResource(R.string.bill_title, it.id) } ?: "") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val detail = state
        if (detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            val sale = detail.sale
            val status = historyStatus(sale)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Header(sale, status)
                HorizontalDivider()
                detail.items.forEach { LineRow(it) }
                HorizontalDivider()
                Amounts(sale, voided = status != HistoryStatus.PAID)
                if (status == HistoryStatus.VOIDED || status == HistoryStatus.CANCELLED) {
                    HorizontalDivider()
                    VoidInfo(sale, status)
                }
                if (status == HistoryStatus.PAID) {
                    Button(
                        onClick = { showVoid = true },
                        enabled = !voiding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.void_sale)) }
                }
            }
        }
    }

    if (showVoid) {
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text(stringResource(R.string.void_sale_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.void_sale_message))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200) },
                        label = { Text(stringResource(R.string.void_reason)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.void(reason)
                        showVoid = false
                        reason = ""
                    },
                    enabled = cleanVoidReason(reason) != null
                ) { Text(stringResource(R.string.void_sale)) }
            },
            dismissButton = {
                TextButton(onClick = { showVoid = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun Header(sale: Sale, status: HistoryStatus?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = saleTitle(sale),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (status != null) {
                Text(
                    text = historyStatusLabel(status),
                    color = historyStatusColor(status),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        val today = LocalDate.now()
        val whenText = formatBillTime(sale.paidAt ?: sale.cancelledAt ?: sale.createdAt, today)
        Text(
            text = "${orderTypeLabel(sale.orderType)} · $whenText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val customer = sale.customerName?.takeIf { it.isNotBlank() }
        if (customer != null && saleTitleIsNotCustomer(sale)) {
            Text(
                text = customer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// The customer is already the title when there is no table, so only repeat it below when the title is the table.
private fun saleTitleIsNotCustomer(sale: Sale): Boolean =
    sale.tableNumber?.isNotBlank() == true && sale.orderType == com.albertsalendah.cafecashier.data.db.OrderType.DINE_IN

@Composable
private fun LineRow(item: SaleItem) {
    val cancelled = item.status == SaleItemStatus.CANCELLED
    val strike = if (cancelled) TextDecoration.LineThrough else null
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${item.quantity} × ${item.productName}",
                textDecoration = strike,
                style = MaterialTheme.typography.bodyLarge
            )
            val details = listOfNotNull(
                item.addOns.takeIf { it.isNotBlank() },
                item.note?.takeIf { it.isNotBlank() },
                if (item.discountPercent > 0) stringResource(R.string.line_discount_percent, item.discountPercent) else null,
                if (cancelled) stringResource(R.string.expense_cancelled) else null
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = formatRupiah(lineNetAmount(item.unitPrice, item.quantity, item.discountPercent)),
            textDecoration = strike
        )
    }
}

// Subtotal, discounts, tax, rounding and the total, then cash received and change. Rows that are 0 are
// left out, so a sale with no tax shows no tax line at all.
@Composable
private fun Amounts(sale: Sale, voided: Boolean) {
    val strike = if (voided) TextDecoration.LineThrough else null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountLine(stringResource(R.string.subtotal), formatRupiah(sale.subtotal))
        if (sale.productDiscountTotal > 0) {
            AmountLine(stringResource(R.string.product_discounts), "-" + formatRupiah(sale.productDiscountTotal))
        }
        if (sale.orderDiscountAmount > 0) {
            AmountLine(stringResource(R.string.order_discount_label), "-" + formatRupiah(sale.orderDiscountAmount))
        }
        if (sale.taxAmount > 0) {
            AmountLine(stringResource(R.string.tax_with_percent, sale.taxPercent), formatRupiah(sale.taxAmount))
        }
        if (sale.rounding != 0L) {
            val sign = if (sale.rounding > 0) "+" else "-"
            AmountLine(stringResource(R.string.rounding), sign + formatRupiah(kotlin.math.abs(sale.rounding)))
        }
        AmountLine(
            label = stringResource(R.string.total),
            value = formatRupiah(sale.total),
            bold = true,
            strike = strike
        )
        val received = sale.cashReceived
        if (received != null) {
            AmountLine(stringResource(R.string.cash_received), formatRupiah(received))
            AmountLine(stringResource(R.string.change_label), formatRupiah(sale.changeAmount ?: 0L))
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, bold: Boolean = false, strike: TextDecoration? = null) {
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style, textDecoration = strike)
    }
}

@Composable
private fun VoidInfo(sale: Sale, status: HistoryStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val at = sale.cancelledAt
        Text(
            text = if (status == HistoryStatus.VOIDED) stringResource(R.string.voided_info_title)
            else stringResource(R.string.cancelled_info_title),
            style = MaterialTheme.typography.titleSmall,
            color = historyStatusColor(status)
        )
        if (at != null) {
            Text(formatBillTime(at), style = MaterialTheme.typography.bodyMedium)
        }
        sale.cancelReason?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stringResource(R.string.void_reason_line, it),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (status == HistoryStatus.VOIDED) {
            Text(
                text = stringResource(R.string.voided_not_counted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
