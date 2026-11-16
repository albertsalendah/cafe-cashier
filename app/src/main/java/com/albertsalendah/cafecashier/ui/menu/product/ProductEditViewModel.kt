package com.albertsalendah.cafecashier.ui.menu.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import com.albertsalendah.cafecashier.data.db.Product
import com.albertsalendah.cafecashier.data.db.ProductDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// productId == null means a new product. Built with its own factory in ProductEditScreen
// because it needs the id (the shared AppViewModelFactory has no arguments).
class ProductEditViewModel(
    private val productDao: ProductDao,
    categoryDao: CategoryDao,
    private val productId: Long?,
    initialCategoryId: Long?
) : ViewModel() {

    // null = still loading.
    val categories: StateFlow<List<Category>?> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var name by mutableStateOf("")
        private set
    var priceText by mutableStateOf("")
        private set
    var categoryId by mutableStateOf(initialCategoryId)
        private set

    // False while an existing product is still being loaded from the database.
    var ready by mutableStateOf(productId == null)
        private set

    // True if the product to edit no longer exists (the screen then just closes).
    var missing by mutableStateOf(false)
        private set

    private var original: Product? = null
    private var busy = false

    val isNew: Boolean get() = productId == null

    private val price: Long? get() = priceText.toLongOrNull()

    val canSave: Boolean
        get() = name.isNotBlank() && (price ?: 0L) > 0L && categoryId != null

    init {
        if (productId != null) {
            viewModelScope.launch {
                val product = productDao.getById(productId)
                if (product == null) {
                    missing = true
                } else {
                    original = product
                    name = product.name
                    priceText = product.price.toString()
                    categoryId = product.categoryId
                }
                ready = true
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
    }

    // Digits only, whole rupiah. 12 digits is far above any real price and fits in a Long.
    fun onPriceChange(value: String) {
        priceText = value.filter { it.isDigit() }.take(12)
    }

    fun onCategoryChange(id: Long) {
        categoryId = id
    }

    fun save(onDone: () -> Unit) {
        val category = categoryId ?: return
        val priceValue = price ?: return
        if (!canSave || busy) return
        busy = true
        viewModelScope.launch {
            val existing = original
            if (existing == null) {
                productDao.insert(Product(categoryId = category, name = name.trim(), price = priceValue))
            } else {
                productDao.update(existing.copy(categoryId = category, name = name.trim(), price = priceValue))
            }
            onDone()
        }
    }

    // Products are never deleted; hiding sets archived = true.
    fun hide(onDone: () -> Unit) {
        val existing = original ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            productDao.update(existing.copy(archived = true))
            onDone()
        }
    }
}
