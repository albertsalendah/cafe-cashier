package com.albertsalendah.cafecashier.domain

/**
 * The product discount percent that applies on [todayEpochDay] (the café's local date as an epoch day).
 * Both ends of the window are inclusive; a missing start or end means that side is open.
 * Returns 0 when there is no discount or today is outside the window.
 */
fun effectiveDiscountPercent(
    discountPercent: Int,
    startDay: Long?,
    endDay: Long?,
    todayEpochDay: Long
): Int {
    if (discountPercent <= 0) return 0
    if (startDay != null && todayEpochDay < startDay) return 0
    if (endDay != null && todayEpochDay > endDay) return 0
    return discountPercent
}
