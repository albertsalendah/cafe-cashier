package com.albertsalendah.cafecashier.domain

import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleStatus

// How a sale in the history is shown. A CANCELLED sale that has a paidAt was paid first and voided
// later; one without a paidAt was a bill cancelled before it was ever paid.
enum class HistoryStatus { PAID, VOIDED, CANCELLED }

// Null for an OPEN bill: open bills belong to the Bills tab, not the history.
fun historyStatus(sale: Sale): HistoryStatus? = when {
    sale.status == SaleStatus.PAID -> HistoryStatus.PAID
    sale.status == SaleStatus.CANCELLED && sale.paidAt != null -> HistoryStatus.VOIDED
    sale.status == SaleStatus.CANCELLED -> HistoryStatus.CANCELLED
    else -> null
}

// The line above one day's history. Only PAID sales count towards the total.
data class HistorySummary(
    val paidCount: Int,
    val paidTotal: Long,
    val voidedCount: Int,
    val cancelledCount: Int
)

fun summarizeHistory(sales: List<Sale>): HistorySummary {
    val paid = sales.filter { historyStatus(it) == HistoryStatus.PAID }
    return HistorySummary(
        paidCount = paid.size,
        paidTotal = paid.sumOf { it.total },
        voidedCount = sales.count { historyStatus(it) == HistoryStatus.VOIDED },
        cancelledCount = sales.count { historyStatus(it) == HistoryStatus.CANCELLED }
    )
}

// A void needs a reason; blank (or only spaces) is not one. Returns the trimmed reason, or null.
fun cleanVoidReason(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }
