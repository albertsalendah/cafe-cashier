package com.albertsalendah.cafecashier.data.db

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// voidPaidSale is plain Kotlin on top of the abstract get/update methods, so it is tested with an
// in-memory stand-in for those (the same approach as SaleWriteDaoTest).
class SaleVoidTest {

    private class FakeDao : SaleWriteDao() {
        val sales = mutableMapOf<Long, Sale>()
        override suspend fun getSale(id: Long): Sale? = sales[id]
        override suspend fun getItems(saleId: Long): List<SaleItem> = emptyList()
        override suspend fun insertSale(sale: Sale): Long = error("not used")
        override suspend fun updateSale(sale: Sale) { sales[sale.id] = sale }
        override suspend fun insertItems(items: List<SaleItem>) = error("not used")
        override suspend fun updateItems(items: List<SaleItem>) = error("not used")
    }

    private fun sale(id: Long, status: SaleStatus) = Sale(
        id = id, status = status, orderType = OrderType.DINE_IN, createdAt = 1_000,
        paidAt = if (status == SaleStatus.OPEN) null else 2_000,
        paymentMethod = if (status == SaleStatus.OPEN) null else PaymentMethod.CASH,
        cashReceived = 50_000, changeAmount = 5_000, subtotal = 45_000, total = 45_000
    )

    @Test
    fun voidingAPaidSaleCancelsItAndKeepsItsPaymentDetails() = runBlocking {
        val dao = FakeDao().apply { sales[1] = sale(1, SaleStatus.PAID) }
        assertTrue(dao.voidPaidSale(1, "Customer returned it", now = 9_000))
        val voided = dao.sales.getValue(1)
        assertEquals(SaleStatus.CANCELLED, voided.status)
        assertEquals("Customer returned it", voided.cancelReason)
        assertEquals(9_000L, voided.cancelledAt)
        assertEquals(2_000L, voided.paidAt)
        assertEquals(PaymentMethod.CASH, voided.paymentMethod)
        assertEquals(45_000L, voided.total)
        assertEquals(50_000L, voided.cashReceived)
    }

    @Test
    fun voidingTwiceChangesNothingTheSecondTime() = runBlocking {
        val dao = FakeDao().apply { sales[1] = sale(1, SaleStatus.PAID) }
        assertTrue(dao.voidPaidSale(1, "first", now = 9_000))
        assertFalse(dao.voidPaidSale(1, "second", now = 99_000))
        assertEquals("first", dao.sales.getValue(1).cancelReason)
        assertEquals(9_000L, dao.sales.getValue(1).cancelledAt)
    }

    @Test
    fun anOpenBillCannotBeVoided() = runBlocking {
        val dao = FakeDao().apply { sales[1] = sale(1, SaleStatus.OPEN) }
        assertFalse(dao.voidPaidSale(1, "x", now = 9_000))
        assertEquals(SaleStatus.OPEN, dao.sales.getValue(1).status)
        assertNull(dao.sales.getValue(1).cancelReason)
    }

    @Test
    fun aMissingSaleReturnsFalse() = runBlocking {
        assertFalse(FakeDao().voidPaidSale(42, "x", now = 1))
    }
}
