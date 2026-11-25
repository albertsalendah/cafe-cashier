package com.albertsalendah.cafecashier.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.OrderDiscount
import com.albertsalendah.cafecashier.data.db.OrderDiscountDao
import com.albertsalendah.cafecashier.data.db.SaleWriteDao
import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.CalcOrderDiscount
import com.albertsalendah.cafecashier.domain.OrderDraft
import com.albertsalendah.cafecashier.domain.SaleAmounts
import com.albertsalendah.cafecashier.domain.buildSale
import com.albertsalendah.cafecashier.domain.calculateSale
import com.albertsalendah.cafecashier.domain.toCalcLines
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// Where the checkout is: still choosing, writing to the database, or paid (with what to show).
sealed interface PayState {
    data object Editing : PayState
    data object Saving : PayState
    data class Paid(val total: Long, val cashReceived: Long, val change: Long) : PayState
}

// Longest cash amount that can be typed (digits). Plenty for rupiah and far from overflowing a Long.
private const val MAX_CASH_DIGITS = 12

class CheckoutViewModel(
    private val settingsDao: SettingsDao,
    private val orderDiscountDao: OrderDiscountDao,
    private val saleWriteDao: SaleWriteDao,
    private val holder: OrderDraftHolder
) : ViewModel() {

    val draft: StateFlow<OrderDraft> = holder.draft

    // The named order discounts to pick from. null until the first load.
    val discounts: StateFlow<List<OrderDiscount>?> = orderDiscountDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedDiscountId = MutableStateFlow<Long?>(null)
    val selectedDiscountId: StateFlow<Long?> = _selectedDiscountId.asStateFlow()

    // The chosen discount as the calculator needs it. If it was deleted meanwhile, none applies.
    val amounts: StateFlow<SaleAmounts> = orderAmounts(
        holder.draft,
        settingsDao.observe(),
        combine(discounts, _selectedDiscountId) { list, id ->
            list?.firstOrNull { it.id == id }?.let { CalcOrderDiscount(it.type, it.value) }
        }
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), calculateSale(emptyList(), null, 0, 1L))

    // The tax rate shown next to the tax amount: "Tax (10%)". Same settings row the amounts use.
    val taxPercent: StateFlow<Int> = settingsDao.observe()
        .map { it?.taxPercent ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // What was typed in the cash field: digits only.
    private val _cashText = MutableStateFlow("")
    val cashText: StateFlow<String> = _cashText.asStateFlow()

    private val _payState = MutableStateFlow<PayState>(PayState.Editing)
    val payState: StateFlow<PayState> = _payState.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    // Tapping the chosen discount again takes it off.
    fun selectDiscount(id: Long?) {
        _selectedDiscountId.value = if (id == _selectedDiscountId.value) null else id
    }

    fun setCashText(text: String) {
        _cashText.value = text.filter { it.isDigit() }.take(MAX_CASH_DIGITS)
    }

    // An empty field counts as 0 (so a total of 0, e.g. a 100% discount, can still be confirmed).
    fun cashAmount(text: String): Long = text.toLongOrNull() ?: 0L

    // Pay in cash: saves the order (a new sale, or the opened bill) as PAID in one transaction.
    // Guarded so a double tap can't pay twice. On success the draft is cleared and the state
    // becomes Paid; on failure nothing was saved and the cashier can try again.
    fun pay() {
        if (_payState.value != PayState.Editing) return
        val current = holder.draft.value
        if (current.activeLines.isEmpty()) return
        val total = amounts.value.total
        val cash = cashAmount(_cashText.value)
        if (cash < total) return
        _payState.value = PayState.Saving
        viewModelScope.launch {
            try {
                val s = settingsDao.get() ?: Settings()
                // Read straight from the database, not from the screen's state.
                val chosenId = _selectedDiscountId.value
                val discount = orderDiscountDao.observeAll().first()
                    .firstOrNull { it.id == chosenId }
                    ?.let { CalcOrderDiscount(it.type, it.value) }
                // Recompute from the very draft being saved, so what is stored is what was calculated.
                val finalAmounts = calculateSale(current.toCalcLines(), discount, s.taxPercent, s.roundingStep)
                if (cash < finalAmounts.total) {
                    _payState.value = PayState.Editing
                    return@launch
                }
                val built = buildSale(
                    draft = current,
                    amounts = finalAmounts,
                    taxPercent = s.taxPercent,
                    orderDiscount = discount,
                    now = System.currentTimeMillis(),
                    cashReceived = cash
                )
                val id = saleWriteDao.saveSale(built.sale, built.items)
                if (id == null) {
                    _messages.tryEmit(R.string.bill_not_open)
                    _payState.value = PayState.Editing
                } else {
                    holder.clear()
                    _payState.value = PayState.Paid(finalAmounts.total, cash, cash - finalAmounts.total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(R.string.pay_failed)
                _payState.value = PayState.Editing
            }
        }
    }
}
