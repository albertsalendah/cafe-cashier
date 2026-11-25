package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRulesTest {

    @Test
    fun taxSwitchedOffAlwaysGivesZeroWhateverTheTextSays() {
        assertEquals(0, parseTaxPercent(enabled = false, text = ""))
        assertEquals(0, parseTaxPercent(enabled = false, text = "11"))
        assertEquals(0, parseTaxPercent(enabled = false, text = "abc"))
    }

    @Test
    fun taxSwitchedOnNeedsAWholePercentFromOneToOneHundred() {
        assertEquals(11, parseTaxPercent(true, "11"))
        assertEquals(11, parseTaxPercent(true, " 11 "))
        assertEquals(1, parseTaxPercent(true, "1"))
        assertEquals(100, parseTaxPercent(true, "100"))
        assertNull(parseTaxPercent(true, ""))
        assertNull(parseTaxPercent(true, "0"))
        assertNull(parseTaxPercent(true, "101"))
        assertNull(parseTaxPercent(true, "-5"))
        assertNull(parseTaxPercent(true, "10.5"))
    }

    @Test
    fun roundingStepNeedsAWholeNumberFromOneUpToTheMaximum() {
        assertEquals(1L, parseRoundingStep("1"))
        assertEquals(100L, parseRoundingStep("100"))
        assertEquals(MAX_ROUNDING_STEP, parseRoundingStep(MAX_ROUNDING_STEP.toString()))
        assertNull(parseRoundingStep(""))
        assertNull(parseRoundingStep("0"))
        assertNull(parseRoundingStep("-100"))
        assertNull(parseRoundingStep((MAX_ROUNDING_STEP + 1).toString()))
        assertNull(parseRoundingStep("1.5"))
    }
}
