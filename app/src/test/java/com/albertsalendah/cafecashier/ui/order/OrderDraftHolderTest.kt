package com.albertsalendah.cafecashier.ui.order

import com.albertsalendah.cafecashier.domain.DraftLine
import com.albertsalendah.cafecashier.domain.OrderDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDraftHolderTest {

    private fun bill() = OrderDraft(
        lines = listOf(
            DraftLine(
                id = 1, productId = 1, categoryId = 1, productName = "A", unitPrice = 1_000,
                discountPercent = 0, quantity = 1, itemId = 10
            )
        ),
        nextLineId = 2, saleId = 5, createdAt = 100
    )

    @Test
    fun anEmptyNewOrderHasNothingToLose() {
        assertFalse(OrderDraftHolder().hasUnsavedChanges())
    }

    @Test
    fun aNewOrderWithLinesHasUnsavedChanges() {
        val holder = OrderDraftHolder()
        holder.update { it.addProduct(1, 1, "A", 1_000, 0) }
        assertTrue(holder.hasUnsavedChanges())
        holder.clear()
        assertFalse(holder.hasUnsavedChanges())
        assertNull(holder.draft.value.saleId)
    }

    @Test
    fun anOpenedBillIsOnlyDirtyOnceItIsChanged() {
        val holder = OrderDraftHolder()
        holder.load(bill())
        assertEquals(5L, holder.draft.value.saleId)
        assertFalse(holder.hasUnsavedChanges())
        holder.update { it.changeQuantity(1, 1) }
        assertTrue(holder.hasUnsavedChanges())
        holder.update { it.changeQuantity(1, -1) }
        assertFalse(holder.hasUnsavedChanges())      // back to what was saved
        holder.update { it.copy(tableNumber = "B2") }
        assertTrue(holder.hasUnsavedChanges())
    }

    @Test
    fun clearForgetsTheOpenedBill() {
        val holder = OrderDraftHolder()
        holder.load(bill())
        holder.clear()
        assertNull(holder.draft.value.saleId)
        assertTrue(holder.draft.value.lines.isEmpty())
        assertFalse(holder.hasUnsavedChanges())
    }
}
