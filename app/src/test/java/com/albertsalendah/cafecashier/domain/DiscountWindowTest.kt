package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscountWindowTest {

    private val start = 20_000L
    private val end = 20_010L

    @Test
    fun insideTheWindowTheDiscountApplies() {
        assertEquals(15, effectiveDiscountPercent(15, start, end, 20_005L))
    }

    @Test
    fun firstAndLastDayAreIncluded() {
        assertEquals(15, effectiveDiscountPercent(15, start, end, start))
        assertEquals(15, effectiveDiscountPercent(15, start, end, end))
    }

    @Test
    fun outsideTheWindowThereIsNoDiscount() {
        assertEquals(0, effectiveDiscountPercent(15, start, end, start - 1))
        assertEquals(0, effectiveDiscountPercent(15, start, end, end + 1))
    }

    @Test
    fun zeroPercentIsAlwaysZero() {
        assertEquals(0, effectiveDiscountPercent(0, start, end, 20_005L))
        assertEquals(0, effectiveDiscountPercent(0, null, null, 20_005L))
    }

    @Test
    fun missingEndMeansOpenEnded() {
        assertEquals(15, effectiveDiscountPercent(15, start, null, 99_999L))
        assertEquals(0, effectiveDiscountPercent(15, start, null, start - 1))
    }

    @Test
    fun missingStartMeansStartsImmediately() {
        assertEquals(15, effectiveDiscountPercent(15, null, end, 1L))
        assertEquals(0, effectiveDiscountPercent(15, null, end, end + 1))
    }

    @Test
    fun noDatesMeansAlwaysOn() {
        assertEquals(15, effectiveDiscountPercent(15, null, null, 20_005L))
    }
}
