package com.albertsalendah.cafecashier.domain

const val MAX_TAX_PERCENT = 100
const val MAX_ROUNDING_STEP = 100_000L

// Tax is optional. Returns the percent to save: 0 when tax is switched off (the whole app then shows no
// tax anywhere), or null when it is switched on but the text is not a whole number from 1 to 100.
fun parseTaxPercent(enabled: Boolean, text: String): Int? {
    if (!enabled) return 0
    val value = text.trim().toIntOrNull() ?: return null
    return value.takeIf { it in 1..MAX_TAX_PERCENT }
}

// The rounding step in rupiah, or null when the text is not a whole number from 1 to MAX_ROUNDING_STEP.
// 1 means "no rounding" (see calculateSale).
fun parseRoundingStep(text: String): Long? {
    val value = text.trim().toLongOrNull() ?: return null
    return value.takeIf { it in 1..MAX_ROUNDING_STEP }
}
