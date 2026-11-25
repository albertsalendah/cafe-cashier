package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpensePool
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleItem
import com.albertsalendah.cafecashier.data.db.SaleItemStatus
import com.albertsalendah.cafecashier.data.db.SaleStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalculatorTest {

    private val zone = ZoneId.of("Asia/Makassar")   // UTC+8
    private val day1 = LocalDate.of(2026, 11, 21).toEpochDay()
    private val day2 = day1 + 1

    private fun millis(day: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun sale(
        id: Long,
        paidAt: Long,
        total: Long,
        subtotal: Long = total,
        productDiscounts: Long = 0,
        orderDiscount: Long = 0,
        tax: Long = 0,
        rounding: Long = 0,
        status: SaleStatus = SaleStatus.PAID
    ) = Sale(
        id = id, status = status, orderType = OrderType.DINE_IN, createdAt = paidAt, paidAt = paidAt,
        subtotal = subtotal, productDiscountTotal = productDiscounts, orderDiscountAmount = orderDiscount,
        taxAmount = tax, rounding = rounding, total = total
    )

    private fun item(
        id: Long, productId: Long, name: String, price: Long, qty: Int,
        discount: Int = 0, status: SaleItemStatus = SaleItemStatus.ACTIVE
    ) = SaleItem(
        id = id, saleId = 1, productId = productId, productName = name, unitPrice = price,
        discountPercent = discount, quantity = qty, status = status
    )

    private fun expense(
        id: Long, day: Long, total: Long, pool: ExpensePool, categoryId: Long = 1, cancelled: Boolean = false
    ) = Expense(
        id = id, categoryId = categoryId, name = "x", unitAmount = total, quantity = 1, total = total,
        pool = pool, day = day, cancelled = cancelled
    )

    private fun report(
        paid: List<Sale> = emptyList(),
        voided: List<Sale> = emptyList(),
        items: List<SaleItem> = emptyList(),
        expenses: List<Expense> = emptyList(),
        names: Map<Long, String> = mapOf(1L to "Ingredients", 2L to "Rent"),
        range: EpochDayRange = EpochDayRange(day1, day2)
    ) = buildReport(range, paid, voided, items, expenses, names, zone)

    @Test
    fun aRangeWithNothingIsAllZeroAndDoesNotFail() {
        val r = report()
        assertEquals(0, r.summary.saleCount)
        assertEquals(0L, r.summary.total)
        assertEquals(0L, r.summary.averageSale)
        assertEquals(0L, r.expenses.total)
        assertEquals(0L, r.balance)
        assertEquals(emptyList<DaySales>(), r.perDay)
        assertEquals(emptyList<ProductSales>(), r.products)
        assertEquals(0, r.voided.count)
    }

    @Test
    fun summaryAddsUpEveryAmountOfThePaidSales() {
        val r = report(
            paid = listOf(
                sale(1, millis(day1, 9), total = 28_000, subtotal = 30_000, productDiscounts = 3_000, tax = 0, rounding = 1_000),
                sale(2, millis(day1, 10), total = 11_100, subtotal = 10_000, orderDiscount = 0, tax = 1_100)
            )
        )
        with(r.summary) {
            assertEquals(2, saleCount)
            assertEquals(40_000L, gross)
            assertEquals(3_000L, productDiscounts)
            assertEquals(1_100L, tax)
            assertEquals(1_000L, rounding)
            assertEquals(39_100L, total)
            assertEquals(19_550L, averageSale)
        }
    }

    @Test
    fun theAverageRoundsHalfUp() {
        val r = report(paid = listOf(sale(1, millis(day1, 9), 10), sale(2, millis(day1, 9), 5)))
        assertEquals(8L, r.summary.averageSale)   // 15 / 2 = 7.5 -> 8
    }

    @Test
    fun salesAreGroupedByTheCafesDayOfPayment() {
        val r = report(
            paid = listOf(
                sale(1, millis(day1, 23, 59), 10_000),   // last minute of day 1 in UTC+8
                sale(2, millis(day2, 0, 0), 20_000),     // first minute of day 2
                sale(3, millis(day2, 8), 5_000)
            )
        )
        assertEquals(listOf(DaySales(day1, 1, 10_000), DaySales(day2, 2, 25_000)), r.perDay)
    }

    @Test
    fun voidedSalesAreCountedApartAndNotInTheTotals() {
        val r = report(
            paid = listOf(sale(1, millis(day1, 9), 10_000)),
            voided = listOf(sale(2, millis(day1, 10), 7_000, status = SaleStatus.CANCELLED))
        )
        assertEquals(10_000L, r.summary.total)
        assertEquals(VoidedSummary(1, 7_000), r.voided)
    }

    @Test
    fun productsAreSummedPerProductWithoutCancelledLines() {
        val r = report(
            items = listOf(
                item(1, productId = 10, name = "Latte", price = 20_000, qty = 2),
                item(2, productId = 10, name = "Latte", price = 20_000, qty = 1),
                item(3, productId = 11, name = "Tea", price = 10_000, qty = 1),
                item(4, productId = 11, name = "Tea", price = 10_000, qty = 5, status = SaleItemStatus.CANCELLED)
            )
        )
        assertEquals(
            listOf(ProductSales(10, "Latte", 3, 60_000), ProductSales(11, "Tea", 1, 10_000)),
            r.products
        )
    }

    @Test
    fun productRevenueIsAfterTheProductDiscount() {
        val r = report(items = listOf(item(1, 10, "Latte", price = 12_550, qty = 1, discount = 10)))
        // 12.550 - 10% (1.255, rounded half up per line to 1.255) = 11.295
        assertEquals(11_295L, r.products.single().revenue)
    }

    @Test
    fun productsAreOrderedByQuantityThenRevenueThenName() {
        val r = report(
            items = listOf(
                item(1, 1, "B", price = 5_000, qty = 2),
                item(2, 2, "A", price = 5_000, qty = 2),
                item(3, 3, "C", price = 9_000, qty = 2),
                item(4, 4, "D", price = 1_000, qty = 9)
            )
        )
        assertEquals(listOf("D", "C", "A", "B"), r.products.map { it.name })
    }

    @Test
    fun aProductShowsItsMostRecentName() {
        val r = report(
            items = listOf(
                item(1, 10, "Late", price = 20_000, qty = 1),
                item(7, 10, "Latte", price = 20_000, qty = 1)
            )
        )
        assertEquals("Latte", r.products.single().name)
    }

    @Test
    fun lineNetAmountMatchesTheSaleCalculator() {
        for ((price, qty, pct) in listOf(Triple(12_550L, 1, 10), Triple(3_333L, 3, 15), Triple(999L, 7, 33), Triple(5_000L, 2, 0))) {
            val calc = calculateSale(listOf(CalcLine(price, qty, pct)), null, 0, 1)
            assertEquals(calc.subtotal - calc.productDiscountTotal, lineNetAmount(price, qty, pct))
        }
    }

    @Test
    fun expensesAreSplitByPoolAndCategoryWithoutCancelledOnes() {
        val r = report(
            expenses = listOf(
                expense(1, day1, 50_000, ExpensePool.DAILY_CASH, categoryId = 1),
                expense(2, day2, 30_000, ExpensePool.DAILY_CASH, categoryId = 1),
                expense(3, day1, 500_000, ExpensePool.OPERATIONAL, categoryId = 2),
                expense(4, day1, 999_000, ExpensePool.DAILY_CASH, categoryId = 1, cancelled = true)
            )
        )
        assertEquals(80_000L, r.expenses.dailyCash)
        assertEquals(500_000L, r.expenses.operational)
        assertEquals(580_000L, r.expenses.total)
        assertEquals(
            listOf(CategoryExpense("Rent", 500_000), CategoryExpense("Ingredients", 80_000)),
            r.expenses.byCategory
        )
    }

    @Test
    fun expensesOutsideTheRangeAreIgnored() {
        val r = report(expenses = listOf(expense(1, day1 - 1, 40_000, ExpensePool.DAILY_CASH)))
        assertEquals(0L, r.expenses.total)
    }

    @Test
    fun balanceIsSalesMinusExpenses() {
        val r = report(
            paid = listOf(sale(1, millis(day1, 9), 100_000)),
            expenses = listOf(expense(1, day1, 30_000, ExpensePool.DAILY_CASH))
        )
        assertEquals(70_000L, r.balance)
    }
}
