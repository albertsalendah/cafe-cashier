package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpensePool

const val MAX_EXPENSE_QUANTITY = 9_999
const val MAX_EXPENSE_UNIT_AMOUNT = 999_999_999_999L   // 12 digits, so amount x quantity can't overflow

// Builds the expense to save, or null when the input isn't valid yet (no category, blank name,
// amount of 0, quantity outside 1..MAX_EXPENSE_QUANTITY). The form uses this both to enable its
// Save button and to build what it saves, so the two can't disagree.
// The total is stored on the row so it never changes retroactively.
fun buildExpense(
    categoryId: Long?,
    name: String,
    unitAmount: Long,
    quantity: Int,
    pool: ExpensePool,
    day: Long
): Expense? {
    val trimmed = name.trim()
    if (categoryId == null || trimmed.isEmpty()) return null
    if (unitAmount !in 1..MAX_EXPENSE_UNIT_AMOUNT) return null
    if (quantity !in 1..MAX_EXPENSE_QUANTITY) return null
    return Expense(
        categoryId = categoryId,
        name = trimmed,
        unitAmount = unitAmount,
        quantity = quantity,
        total = unitAmount * quantity,
        pool = pool,
        day = day
    )
}
