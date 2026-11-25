package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpensePool

// One day's cash picture. Calculated whenever it is needed and never stored, so it can't go out of sync.
// [openingCashSet] is false when no opening cash was entered for the day (it then counts as 0).
data class DayClosing(
    val openingCash: Long,
    val openingCashSet: Boolean,
    val cashSales: Long,
    val dailyCashExpenses: Long,
    val operationalExpenses: Long
) {
    // What should be in the drawer at the end of the day. Operational expenses are paid from
    // elsewhere, so they don't reduce it.
    val expectedCash: Long get() = openingCash + cashSales - dailyCashExpenses
}

// [openingCash] is null when none was entered. [expenses] are the day's expenses; cancelled ones are
// skipped. Works for a day with no sales, no expenses or no opening cash (everything is just 0).
fun calculateClosing(openingCash: Long?, cashSales: Long, expenses: List<Expense>): DayClosing {
    val counted = expenses.filter { !it.cancelled }
    return DayClosing(
        openingCash = openingCash ?: 0L,
        openingCashSet = openingCash != null,
        cashSales = cashSales,
        dailyCashExpenses = counted.filter { it.pool == ExpensePool.DAILY_CASH }.sumOf { it.total },
        operationalExpenses = counted.filter { it.pool == ExpensePool.OPERATIONAL }.sumOf { it.total }
    )
}
