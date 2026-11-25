package com.albertsalendah.cafecashier.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportRangeTest {

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun todayAndYesterday() {
        val today = day(2026, 11, 23)
        assertEquals(EpochDayRange(today, today), presetRange(RangePreset.TODAY, today))
        assertEquals(EpochDayRange(today - 1, today - 1), presetRange(RangePreset.YESTERDAY, today))
    }

    @Test
    fun theWeekStartsOnMondayAndRunsUpToToday() {
        // 23 Nov 2026 is a Monday: the week so far is just that day.
        assertEquals(
            EpochDayRange(day(2026, 11, 23), day(2026, 11, 23)),
            presetRange(RangePreset.THIS_WEEK, day(2026, 11, 23))
        )
        // Sunday 29 Nov 2026 belongs to the week that started on Monday 23 Nov.
        assertEquals(
            EpochDayRange(day(2026, 11, 23), day(2026, 11, 29)),
            presetRange(RangePreset.THIS_WEEK, day(2026, 11, 29))
        )
        // Wednesday 2 Dec 2026: the week began in the previous month.
        assertEquals(
            EpochDayRange(day(2026, 11, 30), day(2026, 12, 2)),
            presetRange(RangePreset.THIS_WEEK, day(2026, 12, 2))
        )
    }

    @Test
    fun theMonthRunsFromTheFirstUpToToday() {
        assertEquals(
            EpochDayRange(day(2026, 11, 1), day(2026, 11, 23)),
            presetRange(RangePreset.THIS_MONTH, day(2026, 11, 23))
        )
        assertEquals(
            EpochDayRange(day(2026, 12, 1), day(2026, 12, 1)),
            presetRange(RangePreset.THIS_MONTH, day(2026, 12, 1))
        )
    }

    @Test
    fun customHasNoFixedRange() {
        assertNull(presetRange(RangePreset.CUSTOM, day(2026, 11, 23)))
    }

    @Test
    fun dayCountIsInclusive() {
        assertEquals(1L, EpochDayRange(10, 10).dayCount)
        assertEquals(7L, EpochDayRange(10, 16).dayCount)
    }

    @Test
    fun pickingAStartAfterTheEndMovesTheEnd() {
        val range = EpochDayRange(10, 20)
        assertEquals(EpochDayRange(15, 20), range.withFrom(15))
        assertEquals(EpochDayRange(25, 25), range.withFrom(25))
    }

    @Test
    fun pickingAnEndBeforeTheStartMovesTheStart() {
        val range = EpochDayRange(10, 20)
        assertEquals(EpochDayRange(10, 15), range.withTo(15))
        assertEquals(EpochDayRange(5, 5), range.withTo(5))
    }
}
