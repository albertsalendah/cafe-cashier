package com.albertsalendah.cafecashier.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class BillTimeFormatTest {

    private fun millis(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun aBillFromTodayShowsOnlyTheTime() {
        val text = formatBillTime(millis(21, 14, 5), LocalDate.of(2026, 11, 21), ZoneOffset.UTC, Locale.US)
        assertEquals("14:05", text)
    }

    @Test
    fun aBillFromAnotherDayAlsoShowsTheDate() {
        val text = formatBillTime(millis(20, 9, 30), LocalDate.of(2026, 11, 21), ZoneOffset.UTC, Locale.US)
        assertEquals("20 Nov, 09:30", text)
    }
}
