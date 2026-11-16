package com.albertsalendah.cafecashier.ui.menu.product

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.domain.DiscountStatus
import com.albertsalendah.cafecashier.ui.formatEpochDay
import com.albertsalendah.cafecashier.ui.theme.StatusColors

data class DiscountLabel(val text: String, val color: Color)

// The words and color for a discount's status, shared by the product list and the product form:
// grey = not started, green = active, orange = ending soon, red = expired. Null = no discount.
@Composable
fun discountLabel(status: DiscountStatus, percent: Int): DiscountLabel? = when (status) {
    DiscountStatus.None -> null

    is DiscountStatus.Scheduled -> DiscountLabel(
        stringResource(R.string.discount_status_scheduled, percent, formatEpochDay(status.startDay)),
        MaterialTheme.colorScheme.onSurfaceVariant
    )

    is DiscountStatus.Active -> {
        val daysLeft = status.daysLeft
        if (daysLeft == null || !status.endingSoon) {
            DiscountLabel(stringResource(R.string.discount_off, percent), StatusColors.green)
        } else when (daysLeft) {
            0 -> DiscountLabel(stringResource(R.string.discount_ends_today, percent), StatusColors.orange)
            1 -> DiscountLabel(stringResource(R.string.discount_ends_tomorrow, percent), StatusColors.orange)
            else -> DiscountLabel(
                stringResource(R.string.discount_ends_in_days, percent, daysLeft),
                StatusColors.orange
            )
        }
    }

    is DiscountStatus.Expired -> DiscountLabel(
        stringResource(R.string.discount_status_expired, formatEpochDay(status.endDay)),
        MaterialTheme.colorScheme.error
    )
}
