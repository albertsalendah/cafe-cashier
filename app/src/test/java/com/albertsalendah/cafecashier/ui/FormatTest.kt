package com.albertsalendah.cafecashier.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatsWholeRupiahWithDotSeparators() {
        assertEquals("Rp 0", formatRupiah(0))
        assertEquals("Rp 500", formatRupiah(500))
        assertEquals("Rp 12.500", formatRupiah(12_500))
        assertEquals("Rp 1.500.000", formatRupiah(1_500_000))
    }

    @Test
    fun groupsDigitsInThrees() {
        assertEquals("", groupThousands(""))
        assertEquals("5", groupThousands("5"))
        assertEquals("500", groupThousands("500"))
        assertEquals("1.000", groupThousands("1000"))
        assertEquals("12.500", groupThousands("12500"))
        assertEquals("125.000", groupThousands("125000"))
        assertEquals("1.500.000", groupThousands("1500000"))
    }

    @Test
    fun groupingAgreesWithFormatRupiah() {
        for (amount in listOf(0L, 7L, 999L, 1_000L, 12_500L, 999_999L, 1_000_000L, 123_456_789_012L)) {
            assertEquals(formatRupiah(amount), "Rp " + groupThousands(amount.toString()))
        }
    }

    @Test
    fun cursorOffsetsFollowTheSeparators() {
        // "12500" is shown as "12.500": cursor positions 0..5 map to 0,1,3,4,5,6.
        val shown = (0..5).map { it + thousandsSeparatorsBefore(5, it) }
        assertEquals(listOf(0, 1, 3, 4, 5, 6), shown)
    }

    @Test
    fun cursorOffsetsNeverCountPastTheLastDigit() {
        for (length in 0..12) {
            val formatted = groupThousands("1".repeat(length))
            assertEquals(formatted.length, length + thousandsSeparatorsBefore(length, length))
        }
    }

    @Test
    fun mappingBackToTheTextGivesTheSameCursor() {
        for (length in 1..12) {
            val formatted = groupThousands("1".repeat(length))
            for (cursor in 0..length) {
                val shown = cursor + thousandsSeparatorsBefore(length, cursor)
                // Same rule the text field uses to go back: count the digits before the position.
                assertEquals(cursor, formatted.take(shown).count { it != '.' })
            }
        }
    }
}
