package com.albertsalendah.cafecashier.ui.menu.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import com.albertsalendah.cafecashier.data.db.ProductDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// One-shot messages for the screen to show once (a snackbar), then clear with messageShown().
enum class CategoryMessage { ArchiveBlockedHasProducts }

class CategoryListViewModel(
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao
) : ViewModel() {

    // null = still loading (so the screen doesn't flash "no categories" before the first query returns).
    // WhileSubscribed(5000) keeps the query alive across a rotation instead of restarting it.
    val categories: StateFlow<List<Category>?> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    // Always queried: the screen needs the count to decide whether to show the "Show hidden" switch.
    val hiddenCategories: StateFlow<List<Category>> = categoryDao.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<CategoryMessage?>(null)
    val message: StateFlow<CategoryMessage?> = _message.asStateFlow()

    fun setShowHidden(show: Boolean) {
        _showHidden.value = show
    }

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { categoryDao.insert(Category(name = trimmed)) }
    }

    fun rename(category: Category, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { categoryDao.update(category.copy(name = trimmed)) }
    }

    // Categories are never deleted. A category with active products can't be hidden.
    fun archive(category: Category) {
        viewModelScope.launch {
            if (productDao.countActiveInCategory(category.id) > 0) {
                _message.value = CategoryMessage.ArchiveBlockedHasProducts
            } else {
                categoryDao.update(category.copy(archived = true))
            }
        }
    }

    // Bring a hidden category back. It moves from the hidden list to the active list by itself,
    // because both lists are live queries.
    fun restore(category: Category) {
        viewModelScope.launch { categoryDao.update(category.copy(archived = false)) }
    }

    fun messageShown() {
        _message.value = null
    }
}
