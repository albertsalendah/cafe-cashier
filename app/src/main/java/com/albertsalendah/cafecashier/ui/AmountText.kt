package com.albertsalendah.cafecashier.ui

// Cleans what the user typed into a money or count field: digits only, at most [maxDigits] of them,
// and no leading zeros. A lone zero is kept ("0" is a valid opening cash). Returns "" for no digits.
fun cleanAmountDigits(raw: String, maxDigits: Int = 12): String {
    val digits = raw.filter { it.isDigit() }.take(maxDigits)
    val trimmed = digits.trimStart('0')
    return if (trimmed.isEmpty() && digits.isNotEmpty()) "0" else trimmed
}
