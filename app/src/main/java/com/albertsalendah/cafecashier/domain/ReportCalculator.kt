package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpensePool
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleItem
import com.albertsalendah.cafecashier.data.db.SaleItemStatus
import java.time.Instant
import java.time.ZoneId

// Everything the Reports screen, the CSV files and the PDF show, as plain numbers. All amounts are whole rupiah.

data class SalesSummary(
    val saleCount: Int,
    val gross: Long,                // sum of Sale.subtotal, before any discount
    val productDiscounts: Long,
    val orderDiscounts: Long,
    val tax: Long,
    val rounding: Long,
    val total: Long                 // gross - discounts + tax + rounding, i.e. what was taken
) {
    // Average per sale, rounded half up; 0 when there are no sales.
    val averageSale: Long get() = if (saleCount == 0) 0L else (total + saleCount / 2) / saleCount
}

data class DaySales(val day: Long, val saleCount: Int, val total: Long)

// [revenue] is after the product discount but before any order discount and tax (those belong to the
// whole sale, not to a product).
data class ProductSales(val productId: Long, val name: String, val quantity: Int, val revenue: Long)

data class CategoryExpense(val categoryName: String, val total: Long)

data class ExpenseSummary(
    val dailyCash: Long,
    val operational: Long,
    val byCategory: List<CategoryExpense>
) {
    val total: Long get() = dailyCash + operational
}

// Sales that were paid in the range and voided afterwards. They are not part of the sales totals.
data class VoidedSummary(val count: Int, val total: Long)

data class SalesReport(
    val range: EpochDayRange,
    val summary: SalesSummary,
    val perDay: List<DaySales>,         // days that had at least one sale, oldest first
    val products: List<ProductSales>,   // every product sold, best first (the screen shows the top 10)
    val expenses: ExpenseSummary,
    val voided: VoidedSummary
) {
    // Sales minus expenses of the range. Not a profit figure: it knows nothing about stock bought earlier.
    val balance: Long get() = summary.total - expenses.total
}

// One line's revenue: price x quantity minus the product discount, rounded half up once per line,
// exactly like calculateSale does for productDiscountTotal.
fun lineNetAmount(unitPrice: Long, quantity: Int, discountPercent: Int): Long {
    val gross = unitPrice * quantity
    val discount = (gross * discountPercent.toLong().coerceIn(0L, 100L) + 50L) / 100L
    return gross - discount
}

// [paidSales] are the PAID sales of the range (by payment time), [voidedSales] the ones paid in the range
// and voided since, [paidItems] the lines of the paid sales. Cancelled lines and cancelled expenses are
// not counted. Days are the café's days in [zone], by payment time.
fun buildReport(
    range: EpochDayRange,
    paidSales: List<Sale>,
    voidedSales: List<Sale>,
    paidItems: List<SaleItem>,
    expenses: List<Expense>,
    categoryNames: Map<Long, String>,
    zone: ZoneId
): SalesReport {
    val summary = SalesSummary(
        saleCount = paidSales.size,
        gross = paidSales.sumOf { it.subtotal },
        productDiscounts = paidSales.sumOf { it.productDiscountTotal },
        orderDiscounts = paidSales.sumOf { it.orderDiscountAmount },
        tax = paidSales.sumOf { it.taxAmount },
        rounding = paidSales.sumOf { it.rounding },
        total = paidSales.sumOf { it.total }
    )

    val perDay = paidSales
        .mapNotNull { sale -> sale.paidAt?.let { dayOf(it, zone) to sale } }
        .groupBy({ it.first }, { it.second })
        .map { (day, sales) -> DaySales(day, sales.size, sales.sumOf { it.total }) }
        .sortedBy { it.day }

    val products = paidItems
        .filter { it.status == SaleItemStatus.ACTIVE && it.quantity > 0 }
        .groupBy { it.productId }
        .map { (productId, lines) ->
            ProductSales(
                productId = productId,
                // The newest line carries the name the product had most recently.
                name = lines.maxBy { it.id }.productName,
                quantity = lines.sumOf { it.quantity },
                revenue = lines.sumOf { lineNetAmount(it.unitPrice, it.quantity, it.discountPercent) }
            )
        }
        .sortedWith(
            compareByDescending<ProductSales> { it.quantity }
                .thenByDescending { it.revenue }
                .thenBy { it.name.lowercase() }
        )

    val counted = expenses.filter { !it.cancelled && it.day in range.fromDay..range.toDay }
    val expenseSummary = ExpenseSummary(
        dailyCash = counted.filter { it.pool == ExpensePool.DAILY_CASH }.sumOf { it.total },
        operational = counted.filter { it.pool == ExpensePool.OPERATIONAL }.sumOf { it.total },
        byCategory = counted
            .groupBy { it.categoryId }
            .map { (id, rows) -> CategoryExpense(categoryNames[id].orEmpty().ifEmpty { "?" }, rows.sumOf { it.total }) }
            .sortedWith(compareByDescending<CategoryExpense> { it.total }.thenBy { it.categoryName.lowercase() })
    )

    return SalesReport(
        range = range,
        summary = summary,
        perDay = perDay,
        products = products,
        expenses = expenseSummary,
        voided = VoidedSummary(voidedSales.size, voidedSales.sumOf { it.total })
    )
}

private fun dayOf(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
