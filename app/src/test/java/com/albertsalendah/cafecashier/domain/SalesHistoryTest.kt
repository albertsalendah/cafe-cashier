package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesHistoryTest {

    private fun sale(
        status: SaleStatus,
        total: Long = 10_000,
        paidAt: Long? = null,
        cancelledAt: Long? = null
    ) = Sale(
        status = status, orderType = OrderType.DINE_IN, createdAt = 1L,
        paidAt = paidAt, cancelledAt = cancelledAt, total = total
    )

    @Test
    fun paidIsPaid() {
        assertEquals(HistoryStatus.PAID, historyStatus(sale(SaleStatus.PAID, paidAt = 5)))
    }

    @Test
    fun cancelledAfterPaymentIsVoided() {
        assertEquals(
            HistoryStatus.VOIDED,
            historyStatus(sale(SaleStatus.CANCELLED, paidAt = 5, cancelledAt = 9))
        )
    }

    @Test
    fun cancelledWithoutPaymentIsCancelledNotVoided() {
        assertEquals(
            HistoryStatus.CANCELLED,
            historyStatus(sale(SaleStatus.CANCELLED, cancelledAt = 9))
        )
    }

    @Test
    fun openBillsAreNotPartOfTheHistory() {
        assertNull(historyStatus(sale(SaleStatus.OPEN)))
    }

    @Test
    fun onlyPaidSalesCountTowardsTheTotal() {
        val summary = summarizeHistory(
            listOf(
                sale(SaleStatus.PAID, total = 20_000, paidAt = 5),
                sale(SaleStatus.PAID, total = 15_000, paidAt = 6),
                sale(SaleStatus.CANCELLED, total = 99_000, paidAt = 7, cancelledAt = 8),
                sale(SaleStatus.CANCELLED, total = 50_000, cancelledAt = 8)
            )
        )
        assertEquals(2, summary.paidCount)
        assertEquals(35_000L, summary.paidTotal)
        assertEquals(1, summary.voidedCount)
        assertEquals(1, summary.cancelledCount)
    }

    @Test
    fun emptyHistoryIsAllZero() {
        val summary = summarizeHistory(emptyList())
        assertEquals(0, summary.paidCount)
        assertEquals(0L, summary.paidTotal)
        assertEquals(0, summary.voidedCount)
        assertEquals(0, summary.cancelledCount)
    }

    @Test
    fun voidReasonIsTrimmedAndMustNotBeBlank() {
        assertEquals("wrong order", cleanVoidReason("  wrong order \n"))
        assertNull(cleanVoidReason("   "))
        assertNull(cleanVoidReason(""))
    }
}
