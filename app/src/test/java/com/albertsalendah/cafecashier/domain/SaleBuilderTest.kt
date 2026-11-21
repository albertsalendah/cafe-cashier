package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.DiscountType
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.PaymentMethod
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleItem
import com.albertsalendah.cafecashier.data.db.SaleItemStatus
import com.albertsalendah.cafecashier.data.db.SaleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleBuilderTest {

    private fun draft() = OrderDraft(orderType = OrderType.DINE_IN, tableNumber = " A3 ", customerName = "  ")
        .addProduct(1, 5, "Bakmi", 13_000, 10)
        .addProduct(1, 5, "Bakmi", 13_000, 10)
        .addProduct(2, 6, "Teh", 5_000, 0)
        .let { it.updateLine(it.lines[1].id, listOf("Es", "Gula"), "less sweet") }

    private fun amounts(d: OrderDraft, discount: CalcOrderDiscount? = null, tax: Int = 10) =
        calculateSale(d.toCalcLines(), discount, tax, 100)

    @Test
    fun anOpenBillHasNoPaymentFields() {
        val d = draft()
        val built = buildSale(d, amounts(d), 10, null, now = 5_000)
        val s = built.sale
        assertEquals(0L, s.id)
        assertEquals(SaleStatus.OPEN, s.status)
        assertNull(s.paidAt); assertNull(s.paymentMethod); assertNull(s.cashReceived); assertNull(s.changeAmount)
        assertEquals(5_000L, s.createdAt)
        assertEquals("A3", s.tableNumber)      // trimmed
        assertNull(s.customerName)             // blank -> null
        assertNull(s.orderDiscountType)
        assertEquals(10, s.taxPercent)
    }

    @Test
    fun aPaidSaleCopiesTheAmountsAndComputesTheChange() {
        val d = draft()
        val discount = CalcOrderDiscount(DiscountType.PERCENT, 10)
        val a = amounts(d, discount)
        val s = buildSale(d, a, 10, discount, now = 9_000, cashReceived = 100_000).sale
        assertEquals(SaleStatus.PAID, s.status)
        assertEquals(9_000L, s.paidAt)
        assertEquals(PaymentMethod.CASH, s.paymentMethod)
        assertEquals(100_000L, s.cashReceived)
        assertEquals(100_000L - a.total, s.changeAmount)
        assertEquals(DiscountType.PERCENT, s.orderDiscountType)
        assertEquals(10L, s.orderDiscountValue)
        assertEquals(a.orderDiscountAmount, s.orderDiscountAmount)
        assertEquals(a.subtotal, s.subtotal)
        assertEquals(a.productDiscountTotal, s.productDiscountTotal)
        assertEquals(a.taxAmount, s.taxAmount)
        assertEquals(a.rounding, s.rounding)
        // the stored amounts reconcile, as the Sale entity promises
        assertEquals(
            s.total,
            s.subtotal - s.productDiscountTotal - s.orderDiscountAmount + s.taxAmount + s.rounding
        )
    }

    @Test
    fun takeawayNeverKeepsATableNumber() {
        val d = draft().copy(orderType = OrderType.TAKEAWAY)
        assertNull(buildSale(d, amounts(d), 10, null, 1).sale.tableNumber)
    }

    @Test
    fun linesAreSavedWithTheirSnapshotAddOnsAndStatus() {
        val d = draft()
        val items = buildSale(d, amounts(d), 10, null, 1).items
        assertEquals(2, items.size)
        assertEquals("Bakmi", items[0].productName)
        assertEquals(2, items[0].quantity)
        assertEquals(10, items[0].discountPercent)
        assertEquals("", items[0].addOns)
        assertEquals("Es, Gula", items[1].addOns)
        assertEquals("less sweet", items[1].note)
        assertTrue(items.all { it.status == SaleItemStatus.ACTIVE && it.id == 0L })
    }

    @Test
    fun existingBillKeepsItsIdsAndCreationTimeAndCancelledLinesStayCancelled() {
        val sale = Sale(id = 9, orderType = OrderType.TAKEAWAY, createdAt = 111)
        val saved = listOf(
            SaleItem(id = 70, saleId = 9, productId = 1, productName = "A", unitPrice = 1_000, quantity = 1),
            SaleItem(id = 71, saleId = 9, productId = 2, productName = "B", unitPrice = 2_000, quantity = 1)
        )
        var d = draftFromSale(sale, saved, mapOf(1L to 5L, 2L to 6L))
        d = d.removeLine(d.lines[1].id).addProduct(3, 7, "C", 3_000, 0)
        val built = buildSale(d, amounts(d, tax = 0), 0, null, now = 99_999)
        assertEquals(9L, built.sale.id)
        assertEquals(111L, built.sale.createdAt)
        assertEquals(listOf(70L, 71L, 0L), built.items.map { it.id })
        assertEquals(
            listOf(SaleItemStatus.ACTIVE, SaleItemStatus.CANCELLED, SaleItemStatus.ACTIVE),
            built.items.map { it.status }
        )
        assertEquals(4_000L, built.sale.total)
    }

    @Test
    fun draftFromSaleRestoresEverythingForEditing() {
        val sale = Sale(
            id = 4, orderType = OrderType.DINE_IN, tableNumber = "T2", customerName = "Budi", createdAt = 321
        )
        val items = listOf(
            SaleItem(
                id = 8, saleId = 4, productId = 1, productName = "Kopi", unitPrice = 15_000,
                discountPercent = 20, quantity = 3, addOns = "Es, Susu", note = "tanpa gula"
            ),
            SaleItem(
                id = 9, saleId = 4, productId = 2, productName = "Roti", unitPrice = 8_000, quantity = 1,
                status = SaleItemStatus.CANCELLED
            )
        )
        val d = draftFromSale(sale, items, mapOf(1L to 5L))
        assertEquals(4L, d.saleId)
        assertEquals(321L, d.createdAt)
        assertEquals("T2", d.tableNumber)
        assertEquals("Budi", d.customerName)
        assertEquals(3L, d.nextLineId)
        val kopi = d.lines[0]
        assertEquals(8L, kopi.itemId)
        assertEquals(5L, kopi.categoryId)
        assertEquals(listOf("Es", "Susu"), kopi.addOns)
        assertEquals("tanpa gula", kopi.note)
        assertEquals(20, kopi.discountPercent)
        assertFalse(kopi.cancelled)
        assertTrue(d.lines[1].cancelled)
        assertEquals(0L, d.lines[1].categoryId)   // unknown product -> no add-on options
        assertEquals(emptyList<String>(), d.lines[1].addOns)
        assertEquals(3, d.itemCount)
        // unchanged right after opening
        assertTrue(d.sameContentAs(d.copy()))
    }

    @Test
    fun quickCashStartsWithTheExactTotalThenRoundNotes() {
        assertEquals(listOf(23_000L, 25_000L, 30_000L, 40_000L, 50_000L), quickCashAmounts(23_000))
        assertEquals(listOf(50_000L, 60_000L, 100_000L), quickCashAmounts(50_000))
        assertEquals(listOf(100_000L), quickCashAmounts(100_000))      // already a round note
        assertEquals(listOf(32_400L, 35_000L, 40_000L, 50_000L, 100_000L), quickCashAmounts(32_400))
        assertTrue(quickCashAmounts(0).isEmpty())
        assertEquals(listOf(23_000L, 25_000L), quickCashAmounts(23_000, max = 2))
    }
}
