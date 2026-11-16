package com.albertsalendah.cafecashier.ui

import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

// The database stores dates as epoch days (a whole calendar date, no time zone).
//
// The Material date picker reports the chosen calendar date as UTC midnight in milliseconds, so the
// two conversions below deliberately use plain UTC arithmetic and NO time zone. Going through the
// device's time zone here is what causes the classic "picked the 16th, saved the 15th" bug.
fun epochDayToPickerMillis(epochDay: Long): Long = epochDay * MILLIS_PER_DAY

// floorDiv (not "/") so dates before 1970 round the right way.
fun pickerMillisToEpochDay(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY)

// The café's local date today, as an epoch day. Uses the device clock and time zone.
fun todayEpochDay(clock: Clock = Clock.systemDefaultZone()): Long = LocalDate.now(clock).toEpochDay()

// 16 Nov 2026
fun formatEpochDay(epochDay: Long, locale: Locale = Locale.getDefault()): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
