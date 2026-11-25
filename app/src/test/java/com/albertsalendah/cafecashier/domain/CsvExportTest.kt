package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpensePool
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    private val zone = ZoneId.of("Asia/Makassar")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun plainTextIsLeftAlone() {
        assertEquals("Latte", csvText("Latte"))
        assertEquals("", csvText(""))
    }

    @Test
    fun commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"Tea, iced\"", csvText("Tea, iced"))
        assertEquals("\"He said \"\"hi\"\"\"", csvText("He said \"hi\""))
        assertEquals("\"a\nb\"", csvText("a\nb"))
    }

    @Test
    fun aLeadingFormulaCharacterIsDefused() {
        assertEquals("'=SUM(A1)", csvText("=SUM(A1)"))
        assertEquals("'+1", csvText("+1"))
        assertEquals("'-1", csvText("-1"))
        assertEquals("'@cmd", csvText("@cmd"))
        assertEquals("a=b", csvText("a=b"))
    }

    @Test
    fun salesCsvHasAHeaderAndOneRowPerSaleOldestFirst() {
        val paid = Sale(
            id = 2, status = SaleStatus.PAID, orderType = OrderType.DINE_IN, tableNumber = "A3",
            customerName = "Budi, Jr", createdAt = at(2026, 11, 21, 9, 0), paidAt = at(2026, 11, 21, 9, 30),
            subtotal = 30_000, productDiscountTotal = 3_000, taxPercent = 10, taxAmount = 2_700,
            rounding = 300, total = 30_000, cashReceived = 50_000, changeAmount = 20_000
        )
        val voided = Sale(
            id = 1, status = SaleStatus.CANCELLED, orderType = OrderType.TAKEAWAY, createdAt = at(2026, 11, 21, 8, 0),
            paidAt = at(2026, 11, 21, 8, 5), cancelledAt = at(2026, 11, 21, 8, 40), cancelReason = "Wrong order",
            subtotal = 10_000, total = 10_000, cashReceived = 10_000, changeAmount = 0
        )
        val csv = salesCsv(listOf(paid, voided), zone)
        val lines = csv.removePrefix(CSV_BOM).split("\r\n").filter { it.isNotEmpty() }
        assertTrue(csv.startsWith(CSV_BOM))
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("sale_id,status,paid_at,"))
        assertEquals("1,VOIDED,2026-11-21 08:05,TAKEAWAY,,,10000,0,0,0,0,0,10000,10000,0,2026-11-21 08:40,Wrong order", lines[1])
        assertEquals("2,PAID,2026-11-21 09:30,DINE_IN,A3,\"Budi, Jr\",30000,3000,0,10,2700,300,30000,50000,20000,,", lines[2])
    }

    @Test
    fun salesWithoutAPaymentAreLeftOut() {
        val neverPaid = Sale(id = 5, status = SaleStatus.CANCELLED, orderType = OrderType.DINE_IN, createdAt = 1, cancelledAt = 2)
        val lines = salesCsv(listOf(neverPaid), zone).removePrefix(CSV_BOM).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(1, lines.size)   // header only
    }

    @Test
    fun productsCsv() {
        val csv = productsCsv(listOf(ProductSales(1, "Latte, large", 3, 60_000), ProductSales(2, "Tea", 1, 10_000)))
        assertEquals(
            CSV_BOM + "product,quantity_sold,revenue\r\n\"Latte, large\",3,60000\r\nTea,1,10000\r\n",
            csv
        )
    }

    @Test
    fun expensesCsvKeepsCancelledRowsMarked() {
        val day = LocalDate.of(2026, 11, 21).toEpochDay()
        val rows = listOf(
            Expense(1, 1, "Milk", 15_000, 2, 30_000, ExpensePool.DAILY_CASH, day),
            Expense(2, 2, "Rent", 500_000, 1, 500_000, ExpensePool.OPERATIONAL, day, cancelled = true)
        )
        val csv = expensesCsv(rows, mapOf(1L to "Ingredients", 2L to "Rent"))
        assertEquals(
            CSV_BOM + "date,category,description,unit_amount,quantity,total,pool,status\r\n" +
                "2026-11-21,Ingredients,Milk,15000,2,30000,DAILY_CASH,ACTIVE\r\n" +
                "2026-11-21,Rent,Rent,500000,1,500000,OPERATIONAL,CANCELLED\r\n",
            csv
        )
    }
}
