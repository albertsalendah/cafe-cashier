package com.albertsalendah.cafecashier.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleDao
import com.albertsalendah.cafecashier.data.db.SaleItem
import com.albertsalendah.cafecashier.data.db.SaleItemDao
import com.albertsalendah.cafecashier.data.db.SaleWriteDao
import com.albertsalendah.cafecashier.domain.cleanVoidReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class SaleDetailState(val sale: Sale, val items: List<SaleItem>)

class SaleDetailViewModel(
    private val saleId: Long,
    saleDao: SaleDao,
    saleItemDao: SaleItemDao,
    private val saleWriteDao: SaleWriteDao,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    // null = still loading or the sale doesn't exist (the screen shows "not found" after a moment of spinner;
    // see [loaded]).
    val state: StateFlow<SaleDetailState?> = combine(
        saleDao.observeById(saleId),
        saleItemDao.observeBySale(saleId)
    ) { sale, items -> sale?.let { SaleDetailState(it, items) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _voiding = MutableStateFlow(false)
    val voiding: StateFlow<Boolean> = _voiding.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun failureShown() {
        _failed.value = false
    }

    // Voids the paid sale. Needs a reason (blank is refused). The sale stays in the history, marked as
    // voided, and no longer counts in the closing cash or the reports. Does nothing twice.
    fun void(reason: String) {
        val clean = cleanVoidReason(reason) ?: return
        if (_voiding.value) return
        _voiding.value = true
        viewModelScope.launch {
            try {
                val ok = saleWriteDao.voidPaidSale(saleId, clean, now())
                if (!ok) _failed.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failed.value = true
            } finally {
                _voiding.value = false
            }
        }
    }
}
