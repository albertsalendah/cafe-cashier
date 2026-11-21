package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.OrderType

/**
 * One line of the order being taken. Name, price and discount are a snapshot of the product at the
 * moment it was added, so a menu edit during the order changes nothing (same rule as a SaleItem).
 * [discountPercent] is the percent that applied then (0 outside its date window).
 *
 * [itemId] is the id of the saved SaleItem when the line came from a saved bill (null for a line
 * added in this draft). A saved line is never deleted, only [cancelled] (shown struck through,
 * not counted, and it can be restored), the same rule as the database.
 */
data class DraftLine(
    val id: Long,                  // unique inside the draft; lets the cart address one line
    val productId: Long,
    val categoryId: Long,          // the product's category, to offer that category's add-on options
    val productName: String,
    val unitPrice: Long,
    val discountPercent: Int,
    val quantity: Int,
    val addOns: List<String> = emptyList(),   // chosen add-on names (saved on the sale line as text)
    val note: String? = null,
    val itemId: Long? = null,
    val cancelled: Boolean = false
)

/**
 * The order being taken, kept in memory until it is saved as a bill or paid.
 * [saleId] and [createdAt] are set when the draft was opened from a saved (open) bill, so saving
 * again updates that bill instead of creating a second one.
 * Plain data and pure functions, so it can be unit tested without Android.
 */
data class OrderDraft(
    val orderType: OrderType = OrderType.DINE_IN,
    val tableNumber: String = "",
    val customerName: String = "",
    val lines: List<DraftLine> = emptyList(),
    val nextLineId: Long = 1,
    val saleId: Long? = null,
    val createdAt: Long? = null
) {
    /** The lines that count (cancelled ones are left out). */
    val activeLines: List<DraftLine> get() = lines.filterNot { it.cancelled }

    /** Total number of items (sum of quantities of the lines that count). */
    val itemCount: Int get() = activeLines.sumOf { it.quantity }

    /**
     * Add one of a product. If the cart already has a plain line for it (same price and discount,
     * no add-ons, no note, not cancelled) its quantity goes up by one; otherwise a new line is added.
     */
    fun addProduct(
        productId: Long,
        categoryId: Long,
        name: String,
        unitPrice: Long,
        discountPercent: Int
    ): OrderDraft {
        val index = lines.indexOfFirst {
            !it.cancelled &&
                it.productId == productId &&
                it.unitPrice == unitPrice &&
                it.discountPercent == discountPercent &&
                it.addOns.isEmpty() &&
                it.note == null
        }
        if (index >= 0) {
            val updated = lines.toMutableList()
            updated[index] = updated[index].copy(quantity = updated[index].quantity + 1)
            return copy(lines = updated)
        }
        val line = DraftLine(
            id = nextLineId,
            productId = productId,
            categoryId = categoryId,
            productName = name,
            unitPrice = unitPrice,
            discountPercent = discountPercent,
            quantity = 1
        )
        return copy(lines = lines + line, nextLineId = nextLineId + 1)
    }

    /**
     * Change a line's quantity by [delta]. A quantity of 0 or less removes the line (see [removeLine]).
     * Unknown or cancelled line: no change.
     */
    fun changeQuantity(lineId: Long, delta: Int): OrderDraft {
        val line = lines.firstOrNull { it.id == lineId && !it.cancelled } ?: return this
        val quantity = line.quantity + delta
        if (quantity <= 0) return removeLine(lineId)
        return copy(lines = lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it })
    }

    /**
     * Take a line out of the order. A line that was never saved is simply deleted; a line that is
     * already saved on a bill is cancelled instead (it stays, struck through, and can be restored).
     */
    fun removeLine(lineId: Long): OrderDraft {
        val line = lines.firstOrNull { it.id == lineId } ?: return this
        if (line.itemId == null) return copy(lines = lines.filterNot { it.id == lineId })
        return copy(lines = lines.map { if (it.id == lineId) it.copy(cancelled = true) else it })
    }

    /** Bring a cancelled line back. Unknown line: no change. */
    fun restoreLine(lineId: Long): OrderDraft =
        copy(lines = lines.map { if (it.id == lineId) it.copy(cancelled = false) else it })

    /** Set a line's add-ons and note. A blank note counts as no note. */
    fun updateLine(lineId: Long, addOns: List<String>, note: String?): OrderDraft =
        copy(
            lines = lines.map {
                if (it.id == lineId) it.copy(addOns = addOns, note = note?.trim()?.ifEmpty { null }) else it
            }
        )

    /** One less of a product: takes one off its most recently added line. No such product: no change. */
    fun decrementProduct(productId: Long): OrderDraft {
        val line = lines.lastOrNull { it.productId == productId && !it.cancelled } ?: return this
        return changeQuantity(line.id, -1)
    }

    /**
     * True if both drafts hold the same order as far as saving is concerned (type, table, customer
     * and lines). The bookkeeping counter is ignored, so "add a product, then remove it again"
     * counts as no change.
     */
    fun sameContentAs(other: OrderDraft): Boolean =
        orderType == other.orderType &&
            tableNumber == other.tableNumber &&
            customerName == other.customerName &&
            lines == other.lines
}

/** The lines as the sale calculator wants them (cancelled lines are flagged, so they are not counted). */
fun OrderDraft.toCalcLines(): List<CalcLine> =
    lines.map {
        CalcLine(
            unitPrice = it.unitPrice,
            quantity = it.quantity,
            discountPercent = it.discountPercent,
            cancelled = it.cancelled
        )
    }
