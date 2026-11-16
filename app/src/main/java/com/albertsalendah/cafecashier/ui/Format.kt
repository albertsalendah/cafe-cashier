package com.albertsalendah.cafecashier.ui

import java.util.Locale

// Whole rupiah with "." as the thousands separator: 12500 -> "Rp 12.500".
fun formatRupiah(amount: Long): String =
    "Rp " + String.format(Locale.US, "%,d", amount).replace(',', '.')

// Groups a string of digits in threes with ".": "12500" -> "12.500". Digits only (no sign).
fun groupThousands(digits: String): String {
    val n = digits.length
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i > 0 && (n - i) % 3 == 0) append('.')
            append(c)
        }
    }
}

// How many "." separators groupThousands puts before the cursor when the cursor sits after
// [offset] digits of a [length]-digit number. Used to keep the cursor in place while typing.
fun thousandsSeparatorsBefore(length: Int, offset: Int): Int =
    (1..minOf(offset, length - 1)).count { (length - it) % 3 == 0 }
