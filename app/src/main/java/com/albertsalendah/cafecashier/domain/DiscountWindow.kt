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

// A discount that ends within this many days (counting the last day itself) is "ending soon".
const val ENDING_SOON_DAYS = 3

/** Where a product's discount stands today. Only for showing it; the checkout uses effectiveDiscountPercent. */
sealed interface DiscountStatus {
    /** No discount (0%). */
    data object None : DiscountStatus

    /** Has a start date that is still in the future. */
    data class Scheduled(val startDay: Long) : DiscountStatus

    /** Applies today. [daysLeft] is the days until the last day (0 = the last day is today); null = no end date. */
    data class Active(val daysLeft: Int?) : DiscountStatus {
        val endingSoon: Boolean get() = daysLeft != null && daysLeft <= ENDING_SOON_DAYS
    }

    /** The end date has passed; the discount no longer applies. */
    data class Expired(val endDay: Long) : DiscountStatus
}

// Active exactly when effectiveDiscountPercent(...) > 0, so what the list shows is what customers get.
fun discountStatus(
    discountPercent: Int,
    startDay: Long?,
    endDay: Long?,
    todayEpochDay: Long
): DiscountStatus {
    if (discountPercent <= 0) return DiscountStatus.None
    if (startDay != null && todayEpochDay < startDay) return DiscountStatus.Scheduled(startDay)
    if (endDay != null && todayEpochDay > endDay) return DiscountStatus.Expired(endDay)
    return DiscountStatus.Active(endDay?.let { (it - todayEpochDay).toInt() })
}
