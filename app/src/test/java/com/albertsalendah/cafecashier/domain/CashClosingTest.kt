package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpensePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashClosingTest {

    private fun expense(total: Long, pool: ExpensePool, cancelled: Boolean = false) = Expense(
        categoryId = 1, name = "x", unitAmount = total, quantity = 1, total = total,
        pool = pool, day = 0, cancelled = cancelled
    )

    @Test
    fun aDayWithNothingIsAllZeroAndDoesNotFail() {
        val closing = calculateClosing(openingCash = null, cashSales = 0, expenses = emptyList())
        assertEquals(0L, closing.openingCash)
        assertFalse(closing.openingCashSet)
        assertEquals(0L, closing.dailyCashExpenses)
        assertEquals(0L, closing.operationalExpenses)
        assertEquals(0L, closing.expectedCash)
    }

    @Test
    fun openingPlusCashSalesMinusDailyCashExpenses() {
        val closing = calculateClosing(
            openingCash = 200_000,
            cashSales = 500_000,
            expenses = listOf(expense(50_000, ExpensePool.DAILY_CASH), expense(25_000, ExpensePool.DAILY_CASH))
        )
        assertTrue(closing.openingCashSet)
        assertEquals(75_000L, closing.dailyCashExpenses)
        assertEquals(625_000L, closing.expectedCash)
    }

    @Test
    fun operationalExpensesAreShownButDoNotReduceTheDrawer() {
        val closing = calculateClosing(
            openingCash = 100_000,
            cashSales = 0,
            expenses = listOf(expense(300_000, ExpensePool.OPERATIONAL))
        )
        assertEquals(300_000L, closing.operationalExpenses)
        assertEquals(0L, closing.dailyCashExpenses)
        assertEquals(100_000L, closing.expectedCash)
    }

    @Test
    fun cancelledExpensesAreNotCounted() {
        val closing = calculateClosing(
            openingCash = 100_000,
            cashSales = 0,
            expenses = listOf(
                expense(40_000, ExpensePool.DAILY_CASH),
                expense(999_000, ExpensePool.DAILY_CASH, cancelled = true),
                expense(999_000, ExpensePool.OPERATIONAL, cancelled = true)
            )
        )
        assertEquals(40_000L, closing.dailyCashExpenses)
        assertEquals(0L, closing.operationalExpenses)
        assertEquals(60_000L, closing.expectedCash)
    }

    @Test
    fun expectedCashCanGoNegative() {
        val closing = calculateClosing(
            openingCash = 0,
            cashSales = 10_000,
            expenses = listOf(expense(30_000, ExpensePool.DAILY_CASH))
        )
        assertEquals(-20_000L, closing.expectedCash)
    }

    @Test
    fun anOpeningCashOfZeroCountsAsSet() {
        val closing = calculateClosing(openingCash = 0, cashSales = 5_000, expenses = emptyList())
        assertTrue(closing.openingCashSet)
        assertEquals(5_000L, closing.expectedCash)
    }
}
