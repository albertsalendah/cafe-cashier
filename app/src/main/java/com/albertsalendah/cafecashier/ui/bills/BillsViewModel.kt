package com.albertsalendah.cafecashier.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.ProductDao
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleDao
import com.albertsalendah.cafecashier.data.db.SaleStatus
import com.albertsalendah.cafecashier.data.db.SaleWriteDao
import com.albertsalendah.cafecashier.domain.draftFromSale
import com.albertsalendah.cafecashier.ui.order.OrderDraftHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class BillsViewModel(
    saleDao: SaleDao,
    private val saleWriteDao: SaleWriteDao,
    private val productDao: ProductDao,
    private val holder: OrderDraftHolder
) : ViewModel() {

    // The open bills, newest first. null until the first load.
    val bills: StateFlow<List<Sale>?> = saleDao.observeByStatus(SaleStatus.OPEN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    // Opening a different bill would throw away an order that has not been saved: the screen asks first.
    fun needsReplaceConfirm(saleId: Long): Boolean =
        holder.draft.value.saleId != saleId && holder.hasUnsavedChanges()

    // Load the bill into the order being edited, then call [onOpened] (the screen opens the cart).
    // If that very bill is already loaded it is left as it is, so unsaved edits are kept.
    fun open(saleId: Long, onOpened: () -> Unit) {
        if (holder.draft.value.saleId == saleId) {
            onOpened()
            return
        }
        viewModelScope.launch {
            try {
                val sale = saleWriteDao.getSale(saleId)
                if (sale == null || sale.status != SaleStatus.OPEN) {
                    _messages.tryEmit(R.string.bill_not_open)
                    return@launch
                }
                val items = saleWriteDao.getItems(saleId)
                val categories = items.map { it.productId }.distinct()
                    .mapNotNull { id -> productDao.getById(id)?.let { id to it.categoryId } }
                    .toMap()
                holder.load(draftFromSale(sale, items, categories))
                onOpened()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(R.string.bill_open_failed)
            }
        }
    }
}
