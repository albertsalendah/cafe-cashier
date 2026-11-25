package com.albertsalendah.cafecashier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class RangePreset { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, CUSTOM }

// A span of whole days as epoch days, both ends inclusive (from == to is a single day).
data class EpochDayRange(val fromDay: Long, val toDay: Long) {
    val dayCount: Long get() = toDay - fromDay + 1
}

// The range for a preset, given today's epoch day. The week starts on Monday; "this week" and "this
// month" run from their first day up to today. CUSTOM has no fixed range, so it returns null.
fun presetRange(preset: RangePreset, today: Long): EpochDayRange? {
    val date = LocalDate.ofEpochDay(today)
    return when (preset) {
        RangePreset.TODAY -> EpochDayRange(today, today)
        RangePreset.YESTERDAY -> EpochDayRange(today - 1, today - 1)
        RangePreset.THIS_WEEK ->
            EpochDayRange(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay(), today)
        RangePreset.THIS_MONTH -> EpochDayRange(date.withDayOfMonth(1).toEpochDay(), today)
        RangePreset.CUSTOM -> null
    }
}

// Picking a new start day: if it is after the end, the end moves with it (the range never inverts).
fun EpochDayRange.withFrom(newFrom: Long): EpochDayRange =
    if (newFrom > toDay) EpochDayRange(newFrom, newFrom) else copy(fromDay = newFrom)

// Picking a new end day: if it is before the start, the start moves with it.
fun EpochDayRange.withTo(newTo: Long): EpochDayRange =
    if (newTo < fromDay) EpochDayRange(newTo, newTo) else copy(toDay = newTo)
