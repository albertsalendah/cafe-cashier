package com.albertsalendah.cafecashier.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayRangeTest {

    private val makassar = ZoneId.of("Asia/Makassar")   // UTC+8, no daylight saving
    private val hour = 3_600_000L

    @Test
    fun aDayStartsAtLocalMidnightNotUtcMidnight() {
        val day = LocalDate.of(2026, 11, 21).toEpochDay()
        val range = dayRangeMillis(day, makassar)
        // Local midnight in UTC+8 is 16:00 UTC the evening before.
        assertEquals(LocalDate.of(2026, 11, 20).atTime(16, 0).toEpochSecond(java.time.ZoneOffset.UTC) * 1000, range.fromMillis)
        assertEquals(24 * hour, range.toMillis - range.fromMillis)
    }

    @Test
    fun neighbouringDaysShareABoundary() {
        val day = LocalDate.of(2026, 11, 21).toEpochDay()
        assertEquals(dayRangeMillis(day, makassar).toMillis, dayRangeMillis(day + 1, makassar).fromMillis)
    }

    @Test
    fun theLastMillisecondBelongsToTheDayAndMidnightToTheNext() {
        val day = LocalDate.of(2026, 11, 21).toEpochDay()
        val today = dayRangeMillis(day, makassar)
        val tomorrow = dayRangeMillis(day + 1, makassar)
        val lastMoment = today.toMillis - 1
        // fromMillis inclusive, toMillis exclusive, as the sale queries use them.
        assertTrue(lastMoment >= today.fromMillis && lastMoment < today.toMillis)
        assertTrue(!(lastMoment >= tomorrow.fromMillis && lastMoment < tomorrow.toMillis))
        assertTrue(today.toMillis >= tomorrow.fromMillis && today.toMillis < tomorrow.toMillis)
    }

    @Test
    fun aDaylightSavingDayIsStillOneCalendarDay() {
        val ny = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8).toEpochDay()   // 23-hour day
        val fallBack = LocalDate.of(2026, 11, 1).toEpochDay()       // 25-hour day
        val short = dayRangeMillis(springForward, ny)
        val long = dayRangeMillis(fallBack, ny)
        assertEquals(23 * hour, short.toMillis - short.fromMillis)
        assertEquals(25 * hour, long.toMillis - long.fromMillis)
    }
}
