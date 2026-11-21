package com.albertsalendah.cafecashier.ui.order

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.domain.OrderDraft

// "Bill #3" or, for a Dine in bill with a table, "Bill #3 · Table A3". Only meant for a draft that
// came from a saved bill; for a new order it returns "New order".
@Composable
fun billLabel(draft: OrderDraft): String {
    val saleId = draft.saleId ?: return stringResource(R.string.new_order)
    val bill = stringResource(R.string.bill_title, saleId)
    val table = draft.tableNumber.trim()
    return if (draft.orderType == OrderType.DINE_IN && table.isNotEmpty()) {
        bill + " · " + stringResource(R.string.bill_table, table)
    } else {
        bill
    }
}
