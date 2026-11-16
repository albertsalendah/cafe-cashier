package com.albertsalendah.cafecashier.ui

import java.util.Locale

// Whole rupiah with "." as the thousands separator: 12500 -> "Rp 12.500".
fun formatRupiah(amount: Long): String =
    "Rp " + String.format(Locale.US, "%,d", amount).replace(',', '.')
