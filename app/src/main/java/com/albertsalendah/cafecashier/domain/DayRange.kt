package com.albertsalendah.cafecashier.domain

import java.time.LocalDate
import java.time.ZoneId

// A span of epoch milliseconds: [fromMillis, toMillis) (start inclusive, end exclusive),
// the shape the sale queries expect.
data class MillisRange(val fromMillis: Long, val toMillis: Long)

// The whole calendar day [epochDay] in the café's time zone, as millisecond boundaries.
// Built from the start of this day and the start of the next (not "+ 24 hours"), so a day
// with a daylight-saving change is still exactly one calendar day, and two neighbouring days
// share a boundary: a payment is counted on one day only.
fun dayRangeMillis(epochDay: Long, zone: ZoneId): MillisRange {
    val date = LocalDate.ofEpochDay(epochDay)
    return MillisRange(
        fromMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        toMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    )
}
