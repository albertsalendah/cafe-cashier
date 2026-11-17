package com.albertsalendah.cafecashier.ui.menu.discount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.DiscountType
import com.albertsalendah.cafecashier.data.db.OrderDiscount
import com.albertsalendah.cafecashier.data.db.OrderDiscountDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrderDiscountListViewModel(
    private val dao: OrderDiscountDao
) : ViewModel() {

    // null = still loading.
    val discounts: StateFlow<List<OrderDiscount>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(name: String, type: DiscountType, value: Long) {
        val clean = cleanValue(type, value) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.insert(OrderDiscount(name = trimmed, type = type, value = clean)) }
    }

    fun update(discount: OrderDiscount, name: String, type: DiscountType, value: Long) {
        val clean = cleanValue(type, value) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.update(discount.copy(name = trimmed, type = type, value = clean)) }
    }

    // A real delete: sales snapshot the type and value they used, so past sales are not affected.
    fun delete(discount: OrderDiscount) {
        viewModelScope.launch { dao.delete(discount) }
    }

    // A discount of 0 does nothing, so it is rejected. A percent is limited to 100; a rupiah
    // amount has no upper limit (the sale calculator never lets it go below zero).
    private fun cleanValue(type: DiscountType, value: Long): Long? = when {
        value <= 0L -> null
        type == DiscountType.PERCENT -> value.coerceAtMost(100L)
        else -> value
    }
}
