package com.albertsalendah.cafecashier.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.data.db.Product
import com.albertsalendah.cafecashier.data.db.ProductDao
import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.OrderDraft
import com.albertsalendah.cafecashier.domain.SaleAmounts
import com.albertsalendah.cafecashier.domain.calculateSale
import com.albertsalendah.cafecashier.domain.effectiveDiscountPercent
import com.albertsalendah.cafecashier.ui.todayEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModel(
    categoryDao: CategoryDao,
    private val productDao: ProductDao,
    settingsDao: SettingsDao,
    private val holder: OrderDraftHolder
) : ViewModel() {

    // null = still loading.
    val categories: StateFlow<List<Category>?> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val picked = MutableStateFlow<Long?>(null)

    // The selected chip. Falls back to the first category when nothing is picked yet, or when the
    // picked category was hidden in the meantime (same rule as the product list).
    val selectedCategoryId: StateFlow<Long?> = combine(categories, picked) { cats, id ->
        when {
            cats == null -> null
            cats.any { it.id == id } -> id
            else -> cats.firstOrNull()?.id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Visible products of the selected category; null = still loading.
    val products: StateFlow<List<Product>?> = selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else productDao.observeActiveByCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The café name and logo for the top bar; null while loading.
    val settings: StateFlow<Settings?> = settingsDao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val draft: StateFlow<OrderDraft> = holder.draft

    // How many of each product are in the cart, for the "x2" badge on the product rows.
    val quantities: StateFlow<Map<Long, Int>> = holder.draft
        .map { d -> d.activeLines.groupBy { it.productId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val amounts: StateFlow<SaleAmounts> = orderAmounts(holder.draft, settingsDao.observe())
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            calculateSale(emptyList(), null, 0, 1L)
        )

    fun selectCategory(id: Long) {
        picked.value = id
    }

    fun setOrderType(type: OrderType) {
        holder.update { it.copy(orderType = type) }
    }

    // Adds one. The discount saved on the line is the one that applies today, by the same window
    // rule the checkout uses.
    fun addProduct(product: Product) {
        val percent = effectiveDiscountPercent(
            product.discountPercent,
            product.discountStartDay,
            product.discountEndDay,
            todayEpochDay()
        )
        holder.update {
            it.addProduct(product.id, product.categoryId, product.name, product.price, percent)
        }
    }

    // True if closing the loaded bill now would lose changes (see OrderDraftHolder).
    fun hasUnsavedChanges(): Boolean = holder.hasUnsavedChanges()

    // Let go of the loaded bill (it stays saved as it was) and start a fresh order.
    fun closeBill() {
        holder.clear()
    }

    // One less of a product (from its most recently added line); the line goes at 0.
    fun decrease(productId: Long) {
        holder.update { it.decrementProduct(productId) }
    }
}
