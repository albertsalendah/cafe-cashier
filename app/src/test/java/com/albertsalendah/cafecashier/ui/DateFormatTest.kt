package com.albertsalendah.cafecashier.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DateFormatTest {

    private val day = LocalDate.of(2026, 11, 16).toEpochDay()

    @Test
    fun pickerMillisRoundTripsToTheSameDay() {
        assertEquals(day, pickerMillisToEpochDay(epochDayToPickerMillis(day)))
    }

    @Test
    fun anyTimeDuringTheUtcDayIsTheSameDay() {
        val midnight = epochDayToPickerMillis(day)
        assertEquals(day, pickerMillisToEpochDay(midnight + 86_399_999L))
        assertEquals(day - 1, pickerMillisToEpochDay(midnight - 1L))
    }

    @Test
    fun datesBefore1970RoundTheRightWay() {
        assertEquals(-1L, pickerMillisToEpochDay(-1L))
        assertEquals(-1L, pickerMillisToEpochDay(epochDayToPickerMillis(-1L)))
    }

    @Test
    fun todayUsesTheLocalDateNotTheUtcDate() {
        // 23:30 UTC on 16 Nov is already 17 Nov in Makassar (UTC+8).
        val clock = Clock.fixed(Instant.parse("2026-11-16T23:30:00Z"), ZoneId.of("Asia/Makassar"))
        assertEquals(LocalDate.of(2026, 11, 17).toEpochDay(), todayEpochDay(clock))
    }

    @Test
    fun formatsAsDayMonthYear() {
        assertEquals("16 Nov 2026", formatEpochDay(day, Locale.US))
    }
}
