package com.albertsalendah.cafecashier.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.formatBillTime
import com.albertsalendah.cafecashier.ui.formatRupiah
import com.albertsalendah.cafecashier.ui.order.orderTypeLabel

// The Bills tab: every open (saved, not yet paid) bill, newest first. Tapping one opens it in the
// cart, where it can be changed, paid or cancelled. If an order that was not saved is being taken,
// the cashier is asked before it is replaced.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(onOpenCart: () -> Unit) {
    val viewModel = viewModel<BillsViewModel>(factory = appViewModelFactory())
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val billList = bills

    // The bill waiting for the "replace the current order?" answer (its id, so it survives rotation).
    var pendingBillId by rememberSaveable { mutableStateOf<Long?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_bills)) }) }
    ) { innerPadding ->
        when {
            billList == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            billList.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.bills_empty)) }

            else -> LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(billList, key = { it.id }) { sale ->
                    BillRow(
                        sale = sale,
                        onClick = {
                            if (viewModel.needsReplaceConfirm(sale.id)) {
                                pendingBillId = sale.id
                            } else {
                                viewModel.open(sale.id, onOpenCart)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pendingBillId?.let { saleId ->
        AlertDialog(
            onDismissRequest = { pendingBillId = null },
            title = { Text(stringResource(R.string.bills_replace_title)) },
            text = { Text(stringResource(R.string.bills_replace_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingBillId = null
                    viewModel.open(saleId, onOpenCart)
                }) { Text(stringResource(R.string.open_bill)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBillId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// Title: the table, else the customer, else "Bill #12". Below: order type, time and the customer
// (when the title is the table). The total is on the right.
@Composable
private fun BillRow(sale: Sale, onClick: () -> Unit) {
    val table = sale.tableNumber?.takeIf { it.isNotBlank() && sale.orderType == OrderType.DINE_IN }
    val customer = sale.customerName?.takeIf { it.isNotBlank() }
    val title = when {
        table != null -> stringResource(R.string.bill_table, table)
        customer != null -> customer
        else -> stringResource(R.string.bill_title, sale.id)
    }
    val details = listOfNotNull(
        orderTypeLabel(sale.orderType),
        formatBillTime(sale.createdAt),
        customer.takeIf { table != null }
    ).joinToString(" · ")

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(details) },
        trailingContent = { Text(formatRupiah(sale.total)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
