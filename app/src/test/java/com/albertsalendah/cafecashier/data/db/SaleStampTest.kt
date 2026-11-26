package com.albertsalendah.cafecashier.data.db

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Who cancelled or voided a sale is saved with it (same fake-DAO approach as SaleVoidTest).
class SaleStampTest {

    private class FakeDao : SaleWriteDao() {
        val sales = mutableMapOf<Long, Sale>()
        override suspend fun getSale(id: Long): Sale? = sales[id]
        override suspend fun getItems(saleId: Long): List<SaleItem> = emptyList()
        override suspend fun insertSale(sale: Sale): Long = error("not used")
        override suspend fun updateSale(sale: Sale) { sales[sale.id] = sale }
        override suspend fun insertItems(items: List<SaleItem>) = error("not used")
        override suspend fun updateItems(items: List<SaleItem>) = error("not used")
    }

    private val sari = Actor(2, "Sari")

    private fun sale(status: SaleStatus) = Sale(
        id = 1, status = status, orderType = OrderType.DINE_IN, createdAt = 1_000,
        paidAt = if (status == SaleStatus.PAID) 2_000 else null,
        createdById = 2, createdByName = "Sari",
        paidById = if (status == SaleStatus.PAID) 3 else null,
        paidByName = if (status == SaleStatus.PAID) "Budi" else null
    )

    @Test
    fun cancellingAnOpenBillRecordsWhoDidIt() = runBlocking {
        val dao = FakeDao().apply { sales[1] = sale(SaleStatus.OPEN) }
        assertTrue(dao.cancelOpenSale(1, "left", now = 5, by = sari))
        val s = dao.sales.getValue(1)
        assertEquals(2L, s.cancelledById)
        assertEquals("Sari", s.cancelledByName)
        assertEquals("Sari", s.createdByName) // the creator is untouched
    }

    @Test
    fun voidingAPaidSaleRecordsWhoDidItAndKeepsWhoCreatedAndPaid() = runBlocking {
        val dao = FakeDao().apply { sales[1] = sale(SaleStatus.PAID) }
        assertTrue(dao.voidPaidSale(1, "wrong", now = 9, by = Actor(1, "Richard")))
        val s = dao.sales.getValue(1)
        assertEquals(1L, s.cancelledById)
        assertEquals("Richard", s.cancelledByName)
        assertEquals("Sari", s.createdByName)
        assertEquals("Budi", s.paidByName)
        assertEquals(3L, s.paidById)
    }

    @Test
    fun withoutAnActorNobodyIsRecorded() = runBlocking {
        val dao = FakeDao().apply { sales[1] = Sale(id = 1, orderType = OrderType.DINE_IN, createdAt = 1) }
        assertTrue(dao.cancelOpenSale(1, "x", now = 5))
        assertNull(dao.sales.getValue(1).cancelledById)
        assertNull(dao.sales.getValue(1).cancelledByName)
    }
}
