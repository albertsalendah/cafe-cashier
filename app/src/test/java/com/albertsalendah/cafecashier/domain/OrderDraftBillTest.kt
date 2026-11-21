package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// How a draft behaves once it was opened from a saved bill: saved lines are cancelled, not deleted.
class OrderDraftBillTest {

    private fun saved(id: Long, productId: Long, price: Long, qty: Int, itemId: Long) = DraftLine(
        id = id, productId = productId, categoryId = 1, productName = "P$productId",
        unitPrice = price, discountPercent = 0, quantity = qty, itemId = itemId
    )

    private fun openBill() = OrderDraft(
        lines = listOf(saved(1, 10, 10_000, 2, itemId = 100), saved(2, 11, 5_000, 1, itemId = 101)),
        nextLineId = 3, saleId = 55, createdAt = 1_000
    )

    @Test
    fun removingASavedLineCancelsItInsteadOfDeletingIt() {
        val draft = openBill().removeLine(1)
        assertEquals(2, draft.lines.size)
        assertTrue(draft.lines.first { it.id == 1L }.cancelled)
        assertFalse(draft.lines.first { it.id == 2L }.cancelled)
    }

    @Test
    fun removingANewLineStillDeletesIt() {
        val draft = openBill().addProduct(12, 1, "New", 7_000, 0)
        val newId = draft.lines.last().id
        val after = draft.removeLine(newId)
        assertEquals(2, after.lines.size)
        assertTrue(after.lines.none { it.id == newId })
    }

    @Test
    fun quantityDownToZeroOnASavedLineCancelsIt() {
        val draft = openBill().changeQuantity(2, -1)
        assertTrue(draft.lines.first { it.id == 2L }.cancelled)
        assertEquals(2, draft.lines.size) // the line is kept
        assertEquals(2, draft.itemCount) // only line 1 (2 x) still counts
    }

    @Test
    fun cancelledLinesAreNotCountedAndNotCalculated() {
        val draft = openBill().removeLine(1)
        assertEquals(1, draft.itemCount)
        assertEquals(1, draft.activeLines.size)
        val amounts = calculateSale(draft.toCalcLines(), null, 0, 1)
        assertEquals(5_000L, amounts.total)
    }

    @Test
    fun restoreBringsACancelledLineBack() {
        val draft = openBill().removeLine(1).restoreLine(1)
        assertFalse(draft.lines.first { it.id == 1L }.cancelled)
        assertEquals(3, draft.itemCount)
    }

    @Test
    fun aCancelledLineCannotBeChangedByQuantity() {
        val cancelled = openBill().removeLine(1)
        assertEquals(cancelled, cancelled.changeQuantity(1, 1))
    }

    @Test
    fun tapsMergeAndDecrementIgnoreCancelledLines() {
        // Product 10 was cancelled; adding it again makes a fresh line instead of reviving the old one.
        val draft = openBill().removeLine(1).addProduct(10, 1, "P10", 10_000, 0)
        assertEquals(3, draft.lines.size)
        assertTrue(draft.lines.first { it.id == 1L }.cancelled)
        val fresh = draft.lines.last()
        assertFalse(fresh.cancelled)
        assertNull(fresh.itemId)
        // Decrementing P10 takes from the active line, not the cancelled one.
        val after = draft.decrementProduct(10)
        assertTrue(after.lines.none { it.productId == 10L && !it.cancelled })
        assertEquals(2, after.lines.size)
    }

    @Test
    fun sameContentIgnoresTheLineCounterButNotRealChanges() {
        val bill = openBill()
        val addedAndRemoved = bill.addProduct(12, 1, "New", 7_000, 0).let { it.removeLine(it.lines.last().id) }
        assertTrue(bill.sameContentAs(addedAndRemoved))
        assertFalse(bill.sameContentAs(bill.copy(tableNumber = "A1")))
        assertFalse(bill.sameContentAs(bill.changeQuantity(1, 1)))
        assertFalse(bill.sameContentAs(bill.removeLine(1)))
    }
}
