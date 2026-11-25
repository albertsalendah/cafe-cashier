package com.albertsalendah.cafecashier.ui.cash.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.ExpenseCategory
import com.albertsalendah.cafecashier.data.db.ExpenseCategoryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Same shape as CategoryListViewModel. Unlike menu categories, an expense category can be hidden
// even if it has expenses: old expenses keep showing their category's name.
class ExpenseCategoryListViewModel(
    private val dao: ExpenseCategoryDao
) : ViewModel() {

    // null = still loading (so the screen doesn't flash "no categories" before the first query returns).
    val categories: StateFlow<List<ExpenseCategory>?> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    // Always queried: the screen needs the count to decide whether to show the "Show hidden" switch.
    val hiddenCategories: StateFlow<List<ExpenseCategory>> = dao.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setShowHidden(show: Boolean) {
        _showHidden.value = show
    }

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.insert(ExpenseCategory(name = trimmed)) }
    }

    fun rename(category: ExpenseCategory, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.update(category.copy(name = trimmed)) }
    }

    fun archive(category: ExpenseCategory) {
        viewModelScope.launch { dao.update(category.copy(archived = true)) }
    }

    fun restore(category: ExpenseCategory) {
        viewModelScope.launch { dao.update(category.copy(archived = false)) }
    }
}
