package com.albertsalendah.cafecashier.ui.menu.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CategoryListViewModel(categoryDao: CategoryDao) : ViewModel() {

    // null = still loading (so the screen doesn't flash "no categories" before the first query returns).
    // WhileSubscribed(5000) keeps the query alive across a rotation instead of restarting it.
    val categories: StateFlow<List<Category>?> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
