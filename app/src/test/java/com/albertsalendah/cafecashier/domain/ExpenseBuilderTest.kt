package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.ExpensePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseBuilderTest {

    private fun build(
        categoryId: Long? = 1,
        name: String = "Milk",
        unitAmount: Long = 15_000,
        quantity: Int = 2,
        pool: ExpensePool = ExpensePool.DAILY_CASH,
        day: Long = 20_000
    ) = buildExpense(categoryId, name, unitAmount, quantity, pool, day)

    @Test
    fun totalIsAmountTimesQuantityAndTheFieldsAreCopied() {
        val e = build()!!
        assertEquals(30_000L, e.total)
        assertEquals(15_000L, e.unitAmount)
        assertEquals(2, e.quantity)
        assertEquals(1L, e.categoryId)
        assertEquals(20_000L, e.day)
        assertEquals(ExpensePool.DAILY_CASH, e.pool)
        assertFalse(e.cancelled)
        assertEquals(0L, e.id)
    }

    @Test
    fun theNameIsTrimmed() {
        assertEquals("Milk", build(name = "  Milk  ")!!.name)
    }

    @Test
    fun theOperationalPoolIsKept() {
        assertEquals(ExpensePool.OPERATIONAL, build(pool = ExpensePool.OPERATIONAL)!!.pool)
    }

    @Test
    fun invalidInputGivesNull() {
        assertNull(build(categoryId = null))
        assertNull(build(name = "   "))
        assertNull(build(unitAmount = 0))
        assertNull(build(unitAmount = -5))
        assertNull(build(quantity = 0))
        assertNull(build(quantity = MAX_EXPENSE_QUANTITY + 1))
        assertNull(build(unitAmount = MAX_EXPENSE_UNIT_AMOUNT + 1))
    }

    @Test
    fun theLargestAllowedValuesDoNotOverflow() {
        val e = build(unitAmount = MAX_EXPENSE_UNIT_AMOUNT, quantity = MAX_EXPENSE_QUANTITY)
        assertNotNull(e)
        assertEquals(MAX_EXPENSE_UNIT_AMOUNT * MAX_EXPENSE_QUANTITY, e!!.total)
    }
}
