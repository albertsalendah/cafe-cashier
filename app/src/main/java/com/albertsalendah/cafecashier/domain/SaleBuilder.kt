package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.PaymentMethod
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleItem
import com.albertsalendah.cafecashier.data.db.SaleItemStatus
import com.albertsalendah.cafecashier.data.db.SaleStatus

/** A sale row with its lines, ready to be written in one go. */
data class SaleWithItems(val sale: Sale, val items: List<SaleItem>)

/** Add-on names are saved on a line as one text, joined with this (and split again when a bill is reopened). */
const val ADD_ON_SEPARATOR = ", "

/**
 * Turns the draft into the rows to save.
 *
 * - [cashReceived] == null: an open bill (status OPEN, nothing about payment).
 * - [cashReceived] given: a paid sale (status PAID, paid now, cash, change = received - total).
 * - [amounts], [taxPercent] and [orderDiscount] are what the calculator produced for exactly this
 *   draft; they are copied onto the sale as they are. An open bill has no order discount.
 * - An existing bill keeps its id and creation time; every line keeps its item id (new lines get 0,
 *   which Room replaces with a new id). The sale id on the items is filled in when they are written.
 * - Cancelled lines are saved as CANCELLED items, so they stay visible in the history.
 * - A table number only counts for Dine In; blank table and customer names are saved as null.
 */
fun buildSale(
    draft: OrderDraft,
    amounts: SaleAmounts,
    taxPercent: Int,
    orderDiscount: CalcOrderDiscount?,
    now: Long,
    cashReceived: Long? = null
): SaleWithItems {
    val paid = cashReceived != null
    val sale = Sale(
        id = draft.saleId ?: 0L,
        status = if (paid) SaleStatus.PAID else SaleStatus.OPEN,
        orderType = draft.orderType,
        tableNumber = if (draft.orderType == OrderType.DINE_IN) draft.tableNumber.trim().ifEmpty { null } else null,
        customerName = draft.customerName.trim().ifEmpty { null },
        createdAt = draft.createdAt ?: now,
        paidAt = if (paid) now else null,
        paymentMethod = if (paid) PaymentMethod.CASH else null,
        cashReceived = cashReceived,
        changeAmount = if (cashReceived != null) cashReceived - amounts.total else null,
        subtotal = amounts.subtotal,
        productDiscountTotal = amounts.productDiscountTotal,
        orderDiscountType = orderDiscount?.type,
        orderDiscountValue = orderDiscount?.value,
        orderDiscountAmount = amounts.orderDiscountAmount,
        taxPercent = taxPercent,
        taxAmount = amounts.taxAmount,
        rounding = amounts.rounding,
        total = amounts.total
    )
    val items = draft.lines.map { line ->
        SaleItem(
            id = line.itemId ?: 0L,
            saleId = draft.saleId ?: 0L,
            productId = line.productId,
            productName = line.productName,
            unitPrice = line.unitPrice,
            discountPercent = line.discountPercent,
            quantity = line.quantity,
            addOns = line.addOns.joinToString(ADD_ON_SEPARATOR),
            note = line.note,
            status = if (line.cancelled) SaleItemStatus.CANCELLED else SaleItemStatus.ACTIVE
        )
    }
    return SaleWithItems(sale, items)
}

/**
 * Turns a saved bill back into a draft so it can be edited, paid or cancelled.
 * [categoryIdByProduct] gives each product's category (for the add-on options); a product that is
 * not in the map gets category 0, which simply has no options.
 */
fun draftFromSale(
    sale: Sale,
    items: List<SaleItem>,
    categoryIdByProduct: Map<Long, Long>
): OrderDraft {
    val lines = items.mapIndexed { index, item ->
        DraftLine(
            id = index + 1L,
            productId = item.productId,
            categoryId = categoryIdByProduct[item.productId] ?: 0L,
            productName = item.productName,
            unitPrice = item.unitPrice,
            discountPercent = item.discountPercent,
            quantity = item.quantity,
            addOns = if (item.addOns.isBlank()) emptyList() else item.addOns.split(ADD_ON_SEPARATOR),
            note = item.note,
            itemId = item.id,
            cancelled = item.status == SaleItemStatus.CANCELLED
        )
    }
    return OrderDraft(
        orderType = sale.orderType,
        tableNumber = sale.tableNumber ?: "",
        customerName = sale.customerName ?: "",
        lines = lines,
        nextLineId = lines.size + 1L,
        saleId = sale.id,
        createdAt = sale.createdAt
    )
}

private val CASH_NOTES = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L)

/**
 * Quick buttons for the cash field: the exact total first, then the next round amounts above it
 * (the total rounded up to a 5.000, 10.000, 20.000, 50.000 or 100.000 note), at most [max] in all.
 * A total of 0 or less has nothing to suggest.
 */
fun quickCashAmounts(total: Long, max: Int = 5): List<Long> {
    if (total <= 0L) return emptyList()
    val rounded = CASH_NOTES
        .map { note -> (total + note - 1) / note * note }
        .filter { it > total }
        .distinct()
    return (listOf(total) + rounded).take(max)
}
