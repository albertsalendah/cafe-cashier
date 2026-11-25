package com.albertsalendah.cafecashier.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {

    @Test
    fun keepsOnlyDigits() {
        assertEquals("12500", cleanAmountDigits("12.500 Rp"))
        assertEquals("", cleanAmountDigits("abc"))
        assertEquals("", cleanAmountDigits(""))
    }

    @Test
    fun removesLeadingZerosButKeepsALoneZero() {
        assertEquals("5", cleanAmountDigits("05"))
        assertEquals("0", cleanAmountDigits("0"))
        assertEquals("0", cleanAmountDigits("000"))
    }

    @Test
    fun limitsTheNumberOfDigits() {
        assertEquals("123456789012", cleanAmountDigits("1234567890123456"))
        assertEquals("9999", cleanAmountDigits("99999", maxDigits = 4))
    }
}
