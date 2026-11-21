package com.albertsalendah.cafecashier.data.db

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// saveSale and cancelOpenSale are plain Kotlin on top of the abstract insert/update/get methods, so
// they are tested here with an in-memory stand-in for those. (Room itself generates the real ones.)
class SaleWriteDaoTest {

    private class FakeDao : SaleWriteDao() {
        val sales = mutableMapOf<Long, Sale>()
        val items = mutableListOf<SaleItem>()
        private var nextSale = 1L
        private var nextItem = 1L

        override suspend fun getSale(id: Long): Sale? = sales[id]
        override suspend fun getItems(saleId: Long): List<SaleItem> = items.filter { it.saleId == saleId }.sortedBy { it.id }
        override suspend fun insertSale(sale: Sale): Long {
            val id = nextSale++
            sales[id] = sale.copy(id = id)
            return id
        }
        override suspend fun updateSale(sale: Sale) { sales[sale.id] = sale }
        override suspend fun insertItems(items: List<SaleItem>) {
            items.forEach { this.items += it.copy(id = nextItem++) }
        }
        override suspend fun updateItems(items: List<SaleItem>) {
            items.forEach { new ->
                val i = this.items.indexOfFirst { it.id == new.id }
                require(i >= 0) { "no such item ${new.id}" }
                this.items[i] = new
            }
        }
    }

    private fun sale(id: Long = 0, status: SaleStatus = SaleStatus.OPEN, total: Long = 0) =
        Sale(id = id, status = status, orderType = OrderType.DINE_IN, createdAt = 1, total = total)

    private fun item(id: Long = 0, name: String = "x", qty: Int = 1, status: SaleItemStatus = SaleItemStatus.ACTIVE) =
        SaleItem(id = id, saleId = 0, productId = 1, productName = name, unitPrice = 1_000, quantity = qty, status = status)

    @Test
    fun aNewSaleIsInsertedWithItsLinesLinkedToIt() = runBlocking {
        val dao = FakeDao()
        val id = dao.saveSale(sale(), listOf(item(name = "a"), item(name = "b")))
        assertEquals(1L, id)
        val saved = dao.getItems(1)
        assertEquals(listOf("a", "b"), saved.map { it.productName })
        assertTrue(saved.all { it.saleId == 1L })
    }

    @Test
    fun savingAnOpenBillAgainUpdatesInPlaceAndAddsNewLines() = runBlocking {
        val dao = FakeDao()
        dao.saveSale(sale(), listOf(item(name = "a"), item(name = "b")))
        val existing = dao.getItems(1)
        val changed = listOf(
            existing[0].copy(quantity = 4),
            existing[1].copy(status = SaleItemStatus.CANCELLED),
            item(name = "c")
        )
        val id = dao.saveSale(sale(id = 1, total = 7_000), changed)
        assertEquals(1L, id)
        assertEquals(1, dao.sales.size)               // no second sale
        assertEquals(7_000L, dao.sales[1L]!!.total)
        val saved = dao.getItems(1)
        assertEquals(3, saved.size)
        assertEquals(4, saved[0].quantity)
        assertEquals(SaleItemStatus.CANCELLED, saved[1].status)
        assertEquals("c", saved[2].productName)
        assertEquals(1L, saved[2].saleId)
    }

    @Test
    fun aSaleThatIsNoLongerOpenIsNotTouched() = runBlocking {
        val dao = FakeDao()
        dao.saveSale(sale(), listOf(item(name = "a")))
        dao.sales[1L] = dao.sales[1L]!!.copy(status = SaleStatus.PAID, total = 500)
        val before = dao.getItems(1)
        assertNull(dao.saveSale(sale(id = 1, total = 9_999), listOf(before[0].copy(quantity = 9))))
        assertEquals(500L, dao.sales[1L]!!.total)
        assertEquals(before, dao.getItems(1))
        // an unknown id is refused too
        assertNull(dao.saveSale(sale(id = 42), emptyList()))
    }

    @Test
    fun payingAnOpenBillTurnsItIntoAPaidSale() = runBlocking {
        val dao = FakeDao()
        dao.saveSale(sale(), listOf(item(name = "a")))
        val paid = sale(id = 1, status = SaleStatus.PAID, total = 1_000).copy(paidAt = 50, cashReceived = 2_000, changeAmount = 1_000)
        assertEquals(1L, dao.saveSale(paid, dao.getItems(1)))
        assertEquals(SaleStatus.PAID, dao.sales[1L]!!.status)
        // and it cannot be changed afterwards
        assertNull(dao.saveSale(sale(id = 1), emptyList()))
    }

    @Test
    fun cancellingOnlyWorksOnOpenBillsAndKeepsTheLines() = runBlocking {
        val dao = FakeDao()
        dao.saveSale(sale(total = 3_000), listOf(item(name = "a")))
        assertTrue(dao.cancelOpenSale(1, "customer left", now = 77))
        val s = dao.sales[1L]!!
        assertEquals(SaleStatus.CANCELLED, s.status)
        assertEquals("customer left", s.cancelReason)
        assertEquals(77L, s.cancelledAt)
        assertEquals(3_000L, s.total)
        assertEquals(1, dao.getItems(1).size)
        assertFalse(dao.cancelOpenSale(1, "again", now = 78))    // already cancelled
        assertFalse(dao.cancelOpenSale(99, "none", now = 78))    // unknown
    }
}
