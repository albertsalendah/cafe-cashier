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
}
