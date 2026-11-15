package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.DiscountType
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleCalculatorTest {

    private fun line(price: Long, qty: Int, percent: Int = 0, cancelled: Boolean = false) =
        CalcLine(unitPrice = price, quantity = qty, discountPercent = percent, cancelled = cancelled)

    @Test
    fun emptyOrderIsAllZero() {
        val result = calculateSale(emptyList(), null, taxPercent = 11, roundingStep = 100)
        assertEquals(SaleAmounts(0, 0, 0, 0, 0, 0), result)
    }

    @Test
    fun plainOrderWithoutDiscountOrTax() {
        val result = calculateSale(listOf(line(12_000, 2)), null, taxPercent = 0, roundingStep = 100)
        assertEquals(SaleAmounts(24_000, 0, 0, 0, 0, 24_000), result)
    }

    @Test
    fun productDiscountRoundsHalfUp() {
        // 12,550 x 15% = 1,882.5 -> 1,883
        val result = calculateSale(listOf(line(12_550, 1, percent = 15)), null, taxPercent = 0, roundingStep = 1)
        assertEquals(SaleAmounts(12_550, 1_883, 0, 0, 0, 10_667), result)
    }

    @Test
    fun productDiscountIsRoundedOncePerLineNotPerUnit() {
        // Per line: 25,100 x 15% = 3,765. Per unit it would be 2 x 1,883 = 3,766.
        val result = calculateSale(listOf(line(12_550, 2, percent = 15)), null, taxPercent = 0, roundingStep = 1)
        assertEquals(SaleAmounts(25_100, 3_765, 0, 0, 0, 21_335), result)
    }

    @Test
    fun hundredPercentMakesTheLineFreeAndMoreIsTreatedAsHundred() {
        val free = calculateSale(listOf(line(10_000, 1, percent = 100)), null, 0, 1)
        assertEquals(SaleAmounts(10_000, 10_000, 0, 0, 0, 0), free)

        val tooHigh = calculateSale(listOf(line(10_000, 1, percent = 150)), null, 0, 1)
        assertEquals(SaleAmounts(10_000, 10_000, 0, 0, 0, 0), tooHigh)
    }

    @Test
    fun cancelledAndZeroQuantityLinesAreIgnored() {
        val lines = listOf(
            line(10_000, 1),
            line(5_000, 3, percent = 10, cancelled = true),
            line(7_000, 0)
        )
        val result = calculateSale(lines, null, taxPercent = 0, roundingStep = 100)
        assertEquals(SaleAmounts(10_000, 0, 0, 0, 0, 10_000), result)
    }

    @Test
    fun orderPercentDiscountComesAfterProductDiscounts() {
        // 10,000 - 10% = 9,000, then order 10% of 9,000 = 900
        val result = calculateSale(
            listOf(line(10_000, 1, percent = 10)),
            CalcOrderDiscount(DiscountType.PERCENT, 10),
            taxPercent = 0,
            roundingStep = 1
        )
        assertEquals(SaleAmounts(10_000, 1_000, 900, 0, 0, 8_100), result)
    }

    @Test
    fun orderPercentDiscountRoundsHalfUp() {
        // 10,050 x 5% = 502.5 -> 503
        val result = calculateSale(
            listOf(line(10_050, 1)),
            CalcOrderDiscount(DiscountType.PERCENT, 5),
            taxPercent = 0,
            roundingStep = 1
        )
        assertEquals(SaleAmounts(10_050, 0, 503, 0, 0, 9_547), result)
    }

    @Test
    fun orderAmountDiscountIsSubtractedAsRupiah() {
        val result = calculateSale(
            listOf(line(10_000, 2)),
            CalcOrderDiscount(DiscountType.AMOUNT, 3_000),
            taxPercent = 0,
            roundingStep = 1
        )
        assertEquals(SaleAmounts(20_000, 0, 3_000, 0, 0, 17_000), result)
    }

    @Test
    fun orderAmountLargerThanTheOrderGivesZeroNotNegative() {
        val result = calculateSale(
            listOf(line(10_000, 1)),
            CalcOrderDiscount(DiscountType.AMOUNT, 15_000),
            taxPercent = 11,
            roundingStep = 100
        )
        assertEquals(SaleAmounts(10_000, 0, 10_000, 0, 0, 0), result)
    }

    @Test
    fun negativeOrderDiscountValueIsIgnored() {
        val result = calculateSale(
            listOf(line(10_000, 1)),
            CalcOrderDiscount(DiscountType.AMOUNT, -500),
            taxPercent = 0,
            roundingStep = 1
        )
        assertEquals(SaleAmounts(10_000, 0, 0, 0, 0, 10_000), result)
    }

    @Test
    fun taxIsAppliedToTheDiscountedAmount() {
        // 20,000 - 10% = 18,000; tax 11% = 1,980
        val result = calculateSale(
            listOf(line(10_000, 2)),
            CalcOrderDiscount(DiscountType.PERCENT, 10),
            taxPercent = 11,
            roundingStep = 1
        )
        assertEquals(SaleAmounts(20_000, 0, 2_000, 1_980, 0, 19_980), result)
    }

    @Test
    fun taxRoundsHalfUp() {
        // 10,050 x 11% = 1,105.5 -> 1,106
        val result = calculateSale(listOf(line(10_050, 1)), null, taxPercent = 11, roundingStep = 1)
        assertEquals(SaleAmounts(10_050, 0, 0, 1_106, 0, 11_156), result)
    }

    @Test
    fun totalIsRoundedUpToTheNextStep() {
        val result = calculateSale(listOf(line(12_550, 1)), null, taxPercent = 0, roundingStep = 100)
        assertEquals(SaleAmounts(12_550, 0, 0, 0, 50, 12_600), result)
    }

    @Test
    fun noRoundingWhenAlreadyOnTheStep() {
        val result = calculateSale(listOf(line(12_500, 1)), null, taxPercent = 0, roundingStep = 100)
        assertEquals(SaleAmounts(12_500, 0, 0, 0, 0, 12_500), result)
    }

    @Test
    fun roundingUsesTheConfiguredStep() {
        val result = calculateSale(listOf(line(12_550, 1)), null, taxPercent = 0, roundingStep = 500)
        assertEquals(SaleAmounts(12_550, 0, 0, 0, 450, 13_000), result)
    }

    @Test
    fun stepOfOneOrLessMeansNoRounding() {
        val one = calculateSale(listOf(line(12_550, 1)), null, taxPercent = 0, roundingStep = 1)
        assertEquals(0L, one.rounding)
        val zero = calculateSale(listOf(line(12_550, 1)), null, taxPercent = 0, roundingStep = 0)
        assertEquals(0L, zero.rounding)
        assertEquals(12_550L, zero.total)
    }

    @Test
    fun mixedOrderReconcilesExactly() {
        val lines = listOf(
            line(12_550, 2, percent = 15),
            line(8_000, 3),
            line(5_000, 1, cancelled = true)
        )
        val result = calculateSale(
            lines,
            CalcOrderDiscount(DiscountType.PERCENT, 5),
            taxPercent = 11,
            roundingStep = 100
        )
        // subtotal 49,100; product discount 3,765 -> 45,335; order 5% = 2,267 -> 43,068;
        // tax 11% = 4,737 -> 47,805; rounding 95 -> 47,900
        assertEquals(SaleAmounts(49_100, 3_765, 2_267, 4_737, 95, 47_900), result)
        assertEquals(
            result.total,
            result.subtotal - result.productDiscountTotal - result.orderDiscountAmount +
                result.taxAmount + result.rounding
        )
    }
}
