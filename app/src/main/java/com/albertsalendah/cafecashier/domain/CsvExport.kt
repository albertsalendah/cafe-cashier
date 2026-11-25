package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// CSV files for the report export. Column names are fixed English (they are a data format, not UI text),
// amounts are plain whole numbers without separators, and every text goes through csvText.

// Excel only reads UTF-8 correctly (names with accents) when the file starts with this mark.
const val CSV_BOM = "﻿"

private val CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

// One text cell. Quoted when it holds a comma, a quote or a line break (quotes doubled). A text that
// starts with = + - @ or a tab/return would be run as a formula by spreadsheets, so it gets a leading '.
fun csvText(value: String): String {
    val guarded = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
}

private fun csvRow(vararg cells: String): String = cells.joinToString(",") + "\r\n"

private fun time(millis: Long?, zone: ZoneId): String =
    if (millis == null) "" else CSV_TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

// One row per sale: the paid ones and the voided ones (status VOIDED, with the void time and reason),
// oldest first. [sales] may mix both.
fun salesCsv(sales: List<Sale>, zone: ZoneId): String = buildString {
    append(CSV_BOM)
    append(
        csvRow(
            "sale_id", "status", "paid_at", "order_type", "table", "customer", "subtotal", "product_discounts",
            "order_discount", "tax_percent", "tax", "rounding", "total", "cash_received", "change",
            "voided_at", "void_reason"
        )
    )
    sales.filter { it.paidAt != null }.sortedWith(compareBy<Sale> { it.paidAt }.thenBy { it.id }).forEach { s ->
        val voided = s.status == SaleStatus.CANCELLED
        append(
            csvRow(
                s.id.toString(),
                if (voided) "VOIDED" else "PAID",
                time(s.paidAt, zone),
                s.orderType.name,
                csvText(s.tableNumber.orEmpty()),
                csvText(s.customerName.orEmpty()),
                s.subtotal.toString(),
                s.productDiscountTotal.toString(),
                s.orderDiscountAmount.toString(),
                s.taxPercent.toString(),
                s.taxAmount.toString(),
                s.rounding.toString(),
                s.total.toString(),
                s.cashReceived?.toString().orEmpty(),
                s.changeAmount?.toString().orEmpty(),
                if (voided) time(s.cancelledAt, zone) else "",
                if (voided) csvText(s.cancelReason.orEmpty()) else ""
            )
        )
    }
}

// One row per product sold (all of them, not only the top 10).
fun productsCsv(products: List<ProductSales>): String = buildString {
    append(CSV_BOM)
    append(csvRow("product", "quantity_sold", "revenue"))
    products.forEach { append(csvRow(csvText(it.name), it.quantity.toString(), it.revenue.toString())) }
}

// One row per expense, cancelled ones included (status CANCELLED) so the file matches the screen.
fun expensesCsv(expenses: List<Expense>, categoryNames: Map<Long, String>): String = buildString {
    append(CSV_BOM)
    append(csvRow("date", "category", "description", "unit_amount", "quantity", "total", "pool", "status"))
    expenses.forEach { e ->
        append(
            csvRow(
                CSV_DATE.format(LocalDate.ofEpochDay(e.day)),
                csvText(categoryNames[e.categoryId].orEmpty()),
                csvText(e.name),
                e.unitAmount.toString(),
                e.quantity.toString(),
                e.total.toString(),
                e.pool.name,
                if (e.cancelled) "CANCELLED" else "ACTIVE"
            )
        )
    }
}
