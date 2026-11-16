package com.albertsalendah.cafecashier.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.ui.menu.category.CategoryListViewModel

// Manual dependency wiring (no Hilt): every ViewModel is built from the app's single database.
// Add one branch per new ViewModel.
class AppViewModelFactory(private val app: CafeCashierApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CategoryListViewModel::class.java) ->
            CategoryListViewModel(app.database.categoryDao(), app.database.productDao()) as T

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

// Usage inside a screen: viewModel<SomeViewModel>(factory = appViewModelFactory())
@Composable
fun appViewModelFactory(): AppViewModelFactory {
    val app = LocalContext.current.applicationContext as CafeCashierApp
    return remember(app) { AppViewModelFactory(app) }
}
