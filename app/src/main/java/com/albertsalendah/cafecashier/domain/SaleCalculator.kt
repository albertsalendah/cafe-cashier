package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.DiscountType

/**
 * One order line as the calculator sees it.
 * [discountPercent] is the percent that applies right now (0 if none or outside its date window,
 * see [effectiveDiscountPercent]); it is also what gets saved on the SaleItem.
 */
data class CalcLine(
    val unitPrice: Long,
    val quantity: Int,
    val discountPercent: Int = 0,
    val cancelled: Boolean = false
)

/** [value] is a whole percent for PERCENT and whole rupiah for AMOUNT. */
data class CalcOrderDiscount(
    val type: DiscountType,
    val value: Long
)

/**
 * Every amount a Sale row stores, all whole rupiah.
 * subtotal - productDiscountTotal - orderDiscountAmount + taxAmount + rounding = total
 */
data class SaleAmounts(
    val subtotal: Long,
    val productDiscountTotal: Long,
    val orderDiscountAmount: Long,
    val taxAmount: Long,
    val rounding: Long,
    val total: Long
)

/**
 * Pure sale math, the only place it lives. An open bill is always recomputed from its lines.
 *
 * Order: per-line product discount -> order discount -> tax -> rounding of the final total.
 * - Percent amounts (product discount, percent order discount, tax) are rounded half up to a whole
 *   rupiah, once per line for the product discount and once for the order discount and for the tax.
 * - An amount order discount can never push the total below zero.
 * - The total is rounded UP to the next multiple of [roundingStep]; a step of 1 or less means no rounding.
 * - Cancelled lines and lines with a quantity of 0 are ignored.
 */
fun calculateSale(
    lines: List<CalcLine>,
    orderDiscount: CalcOrderDiscount?,
    taxPercent: Int,
    roundingStep: Long
): SaleAmounts {
    var subtotal = 0L
    var productDiscountTotal = 0L
    for (line in lines) {
        if (line.cancelled || line.quantity <= 0) continue
        val gross = line.unitPrice * line.quantity
        subtotal += gross
        productDiscountTotal += percentOf(gross, line.discountPercent.toLong())
    }
    val afterProductDiscounts = subtotal - productDiscountTotal

    val orderDiscountAmount = if (orderDiscount == null) {
        0L
    } else {
        when (orderDiscount.type) {
            DiscountType.PERCENT -> percentOf(afterProductDiscounts, orderDiscount.value)
            DiscountType.AMOUNT -> orderDiscount.value.coerceIn(0L, afterProductDiscounts)
        }
    }
    val discounted = afterProductDiscounts - orderDiscountAmount

    val taxAmount = percentOf(discounted, taxPercent.toLong())
    val beforeRounding = discounted + taxAmount

    val rounding = roundingUpToStep(beforeRounding, roundingStep)
    return SaleAmounts(
        subtotal = subtotal,
        productDiscountTotal = productDiscountTotal,
        orderDiscountAmount = orderDiscountAmount,
        taxAmount = taxAmount,
        rounding = rounding,
        total = beforeRounding + rounding
    )
}

/** amount x percent / 100 rounded half up. The percent is limited to 0..100; the amount is never negative. */
private fun percentOf(amount: Long, percent: Long): Long =
    (amount * percent.coerceIn(0L, 100L) + 50L) / 100L

/** How much to add to [value] to reach the next multiple of [step] (0 if already on it or step <= 1). */
private fun roundingUpToStep(value: Long, step: Long): Long {
    if (step <= 1L) return 0L
    val remainder = value % step
    return if (remainder == 0L) 0L else step - remainder
}
