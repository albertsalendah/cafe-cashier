package com.albertsalendah.cafecashier.ui.menu.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import com.albertsalendah.cafecashier.data.db.Product
import com.albertsalendah.cafecashier.data.db.ProductDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ProductListViewModel(
    categoryDao: CategoryDao,
    private val productDao: ProductDao
) : ViewModel() {

    // null = still loading.
    val categories: StateFlow<List<Category>?> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val picked = MutableStateFlow<Long?>(null)

    // The chip that is selected. Falls back to the first category when nothing is picked yet,
    // or when the picked category was hidden in the meantime.
    val selectedCategoryId: StateFlow<Long?> = combine(categories, picked) { cats, id ->
        when {
            cats == null -> null
            cats.any { it.id == id } -> id
            else -> cats.firstOrNull()?.id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // null = still loading. Switches to the new category's query whenever the chip changes.
    val products: StateFlow<List<Product>?> = selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else productDao.observeActiveByCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCategory(id: Long) {
        picked.value = id
    }
}
