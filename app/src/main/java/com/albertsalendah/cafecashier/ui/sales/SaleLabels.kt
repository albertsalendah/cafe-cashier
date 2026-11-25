package com.albertsalendah.cafecashier.ui.sales

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.domain.HistoryStatus
import com.albertsalendah.cafecashier.ui.theme.StatusColors

// "Table A3", else the customer, else "Bill #12" (same rule as the Bills tab).
@Composable
fun saleTitle(sale: Sale): String {
    val table = sale.tableNumber?.takeIf { it.isNotBlank() && sale.orderType == OrderType.DINE_IN }
    val customer = sale.customerName?.takeIf { it.isNotBlank() }
    return when {
        table != null -> stringResource(R.string.bill_table, table)
        customer != null -> customer
        else -> stringResource(R.string.bill_title, sale.id)
    }
}

@Composable
fun historyStatusLabel(status: HistoryStatus): String = stringResource(
    when (status) {
        HistoryStatus.PAID -> R.string.sale_status_paid
        HistoryStatus.VOIDED -> R.string.sale_status_voided
        HistoryStatus.CANCELLED -> R.string.sale_status_cancelled
    }
)

// Colour is never the only signal: the label always says the status too.
@Composable
fun historyStatusColor(status: HistoryStatus): Color = when (status) {
    HistoryStatus.PAID -> StatusColors.green
    HistoryStatus.VOIDED -> MaterialTheme.colorScheme.error
    HistoryStatus.CANCELLED -> StatusColors.orange
}
