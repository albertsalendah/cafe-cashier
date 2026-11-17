package com.albertsalendah.cafecashier.ui.menu.addon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.AddOnOption
import com.albertsalendah.cafecashier.data.db.AddOnOptionDao
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AddOnListViewModel(
    categoryDao: CategoryDao,
    private val addOnDao: AddOnOptionDao
) : ViewModel() {

    // null = still loading. Only visible categories: add-ons of a hidden category come back with it.
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

    // null = still loading. Switches to the new category's query whenever the chip changes.
    val addOns: StateFlow<List<AddOnOption>?> = selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else addOnDao.observeByCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCategory(id: Long) {
        picked.value = id
    }

    fun add(categoryId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { addOnDao.insert(AddOnOption(categoryId = categoryId, name = trimmed)) }
    }

    fun rename(option: AddOnOption, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { addOnDao.update(option.copy(name = trimmed)) }
    }

    // A real delete: sale lines keep the chosen add-ons as text, so past sales are not affected.
    fun delete(option: AddOnOption) {
        viewModelScope.launch { addOnDao.delete(option) }
    }
}
