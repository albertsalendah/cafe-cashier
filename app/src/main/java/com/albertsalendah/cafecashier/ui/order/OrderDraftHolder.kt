package com.albertsalendah.cafecashier.ui.order

import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.domain.CalcOrderDiscount
import com.albertsalendah.cafecashier.domain.OrderDraft
import com.albertsalendah.cafecashier.domain.SaleAmounts
import com.albertsalendah.cafecashier.domain.calculateSale
import com.albertsalendah.cafecashier.domain.toCalcLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

// The order being taken. One instance for the whole app (CafeCashierApp.orderDraftHolder), so the
// Order screen, the cart and the checkout all see the same draft and it survives tab switches.
// It lives in memory only: it is gone if Android kills the app, which is why a bill can be saved.
class OrderDraftHolder {
    private val _draft = MutableStateFlow(OrderDraft())
    val draft: StateFlow<OrderDraft> = _draft.asStateFlow()

    // The draft as it was when it was opened from a saved bill (null for a brand new order).
    private var baseline: OrderDraft? = null

    fun update(transform: (OrderDraft) -> OrderDraft) {
        _draft.update(transform)
    }

    // Start a fresh order (everything back to the defaults).
    fun clear() {
        baseline = null
        _draft.value = OrderDraft()
    }

    // Replace the order with a saved bill that was just opened.
    fun load(opened: OrderDraft) {
        baseline = opened
        _draft.value = opened
    }

    // True if there is something that would be lost by clearing: a new order that has lines, or a
    // saved bill that was changed since it was opened.
    fun hasUnsavedChanges(): Boolean {
        val current = _draft.value
        val opened = baseline ?: return current.lines.isNotEmpty()
        return !current.sameContentAs(opened)
    }
}

// The amounts for the draft, recomputed with calculateSale whenever the draft, the settings or the
// chosen order discount change. Tax and rounding come from Settings (a missing row counts as the
// defaults). The cart passes no order discount: it is chosen at checkout.
fun orderAmounts(
    draft: Flow<OrderDraft>,
    settings: Flow<Settings?>,
    orderDiscount: Flow<CalcOrderDiscount?>? = null
): Flow<SaleAmounts> {
    val discount: Flow<CalcOrderDiscount?> = orderDiscount ?: MutableStateFlow<CalcOrderDiscount?>(null)
    return combine(draft, settings, discount) { currentDraft, currentSettings, currentDiscount ->
        val s = currentSettings ?: Settings()
        calculateSale(
            lines = currentDraft.toCalcLines(),
            orderDiscount = currentDiscount,
            taxPercent = s.taxPercent,
            roundingStep = s.roundingStep
        )
    }
}
