package com.albertsalendah.cafecashier.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.AddOnOptionDao
import com.albertsalendah.cafecashier.data.db.SaleWriteDao
import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.OrderDraft
import com.albertsalendah.cafecashier.domain.SaleAmounts
import com.albertsalendah.cafecashier.domain.buildSale
import com.albertsalendah.cafecashier.domain.calculateSale
import com.albertsalendah.cafecashier.domain.toCalcLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class CartViewModel(
    private val settingsDao: SettingsDao,
    private val addOnOptionDao: AddOnOptionDao,
    private val saleWriteDao: SaleWriteDao,
    private val holder: OrderDraftHolder
) : ViewModel() {

    val draft: StateFlow<OrderDraft> = holder.draft

    val amounts: StateFlow<SaleAmounts> = orderAmounts(holder.draft, settingsDao.observe())
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            calculateSale(emptyList(), null, 0, 1L)
        )

    // One-shot messages for a snackbar: the string resource to show.
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    // Both are optional free text (a table can be "A3" or "Terrace").
    fun setTableNumber(text: String) {
        holder.update { it.copy(tableNumber = text) }
    }

    fun setCustomerName(text: String) {
        holder.update { it.copy(customerName = text) }
    }

    // +1 or -1 on one line; a line that reaches 0 is removed (a saved line is cancelled instead).
    fun changeQuantity(lineId: Long, delta: Int) {
        holder.update { it.changeQuantity(lineId, delta) }
    }

    fun removeLine(lineId: Long) {
        holder.update { it.removeLine(lineId) }
    }

    fun restoreLine(lineId: Long) {
        holder.update { it.restoreLine(lineId) }
    }

    fun updateLine(lineId: Long, addOns: List<String>, note: String) {
        holder.update { it.updateLine(lineId, addOns, note) }
    }

    // The add-on options of a category (names only, sorted), for the line editor.
    fun addOnNames(categoryId: Long): Flow<List<String>> =
        addOnOptionDao.observeByCategory(categoryId).map { options -> options.map { it.name } }

    // Throw the whole order away and start fresh. A saved bill stays saved as it was.
    fun clear() {
        holder.clear()
    }

    // True if closing now would lose something (see OrderDraftHolder).
    fun hasUnsavedChanges(): Boolean = holder.hasUnsavedChanges()

    // Save the order as an open bill (a new one, or the saved bill that was opened). On success the
    // draft is cleared, which takes the cart back to where it came from. Needs at least one line
    // that counts.
    fun saveBill() {
        val current = holder.draft.value
        if (current.activeLines.isEmpty()) return
        viewModelScope.launch {
            try {
                val s = settingsDao.get() ?: Settings()
                val amounts = calculateSale(current.toCalcLines(), null, s.taxPercent, s.roundingStep)
                val built = buildSale(
                    draft = current,
                    amounts = amounts,
                    taxPercent = s.taxPercent,
                    orderDiscount = null,
                    now = System.currentTimeMillis()
                )
                val id = saleWriteDao.saveSale(built.sale, built.items)
                if (id == null) _messages.tryEmit(R.string.bill_not_open) else holder.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(R.string.bill_save_failed)
            }
        }
    }

    // Cancel the saved bill that is open here, with the cashier's reason. On success the draft is
    // cleared. Does nothing for an order that was never saved (that is simply cleared instead).
    fun cancelBill(reason: String) {
        val saleId = holder.draft.value.saleId ?: return
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val done = saleWriteDao.cancelOpenSale(saleId, trimmed, System.currentTimeMillis())
                if (done) holder.clear() else _messages.tryEmit(R.string.bill_not_open)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(R.string.cancel_bill_failed)
            }
        }
    }
}
