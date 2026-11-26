package com.albertsalendah.cafecashier.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.ui.backup.BackupViewModel
import com.albertsalendah.cafecashier.ui.bills.BillsViewModel
import com.albertsalendah.cafecashier.ui.cash.CashViewModel
import com.albertsalendah.cafecashier.ui.cash.category.ExpenseCategoryListViewModel
import com.albertsalendah.cafecashier.ui.menu.addon.AddOnListViewModel
import com.albertsalendah.cafecashier.ui.menu.category.CategoryListViewModel
import com.albertsalendah.cafecashier.ui.menu.discount.OrderDiscountListViewModel
import com.albertsalendah.cafecashier.ui.menu.product.ProductListViewModel
import com.albertsalendah.cafecashier.ui.order.CartViewModel
import com.albertsalendah.cafecashier.ui.order.CheckoutViewModel
import com.albertsalendah.cafecashier.ui.order.OrderViewModel
import com.albertsalendah.cafecashier.ui.reports.ReportsViewModel
import com.albertsalendah.cafecashier.ui.sales.SalesViewModel
import com.albertsalendah.cafecashier.ui.settings.SettingsViewModel

// Manual dependency wiring (no Hilt): every ViewModel is built from the app's single database.
// Add one branch per new ViewModel that needs no screen arguments. A ViewModel that needs an
// argument (like ProductEditViewModel, which needs the product id) is built with its own
// viewModelFactory { initializer { ... } } inside its screen instead.
class AppViewModelFactory(private val app: CafeCashierApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CategoryListViewModel::class.java) ->
            CategoryListViewModel(app.database.categoryDao(), app.database.productDao()) as T

        modelClass.isAssignableFrom(ProductListViewModel::class.java) ->
            ProductListViewModel(app.database.categoryDao(), app.database.productDao()) as T

        modelClass.isAssignableFrom(AddOnListViewModel::class.java) ->
            AddOnListViewModel(app.database.categoryDao(), app.database.addOnOptionDao()) as T

        modelClass.isAssignableFrom(OrderDiscountListViewModel::class.java) ->
            OrderDiscountListViewModel(app.database.orderDiscountDao()) as T

        modelClass.isAssignableFrom(OrderViewModel::class.java) ->
            OrderViewModel(
                app.database.categoryDao(),
                app.database.productDao(),
                app.database.settingsDao(),
                app.orderDraftHolder
            ) as T

        modelClass.isAssignableFrom(CartViewModel::class.java) ->
            CartViewModel(
                app.database.settingsDao(),
                app.database.addOnOptionDao(),
                app.database.saleWriteDao(),
                app.orderDraftHolder
            ) as T

        modelClass.isAssignableFrom(CheckoutViewModel::class.java) ->
            CheckoutViewModel(
                app.database.settingsDao(),
                app.database.orderDiscountDao(),
                app.database.saleWriteDao(),
                app.orderDraftHolder
            ) as T

        modelClass.isAssignableFrom(BillsViewModel::class.java) ->
            BillsViewModel(
                app.database.saleDao(),
                app.database.saleWriteDao(),
                app.database.productDao(),
                app.orderDraftHolder
            ) as T

        modelClass.isAssignableFrom(CashViewModel::class.java) ->
            CashViewModel(
                app.database.dailyCashDao(),
                app.database.expenseDao(),
                app.database.expenseCategoryDao(),
                app.database.saleDao()
            ) as T

        modelClass.isAssignableFrom(ExpenseCategoryListViewModel::class.java) ->
            ExpenseCategoryListViewModel(app.database.expenseCategoryDao()) as T

        modelClass.isAssignableFrom(SalesViewModel::class.java) ->
            SalesViewModel(app.database.saleDao()) as T

        modelClass.isAssignableFrom(ReportsViewModel::class.java) ->
            ReportsViewModel(
                app.database.saleDao(),
                app.database.saleItemDao(),
                app.database.expenseDao(),
                app.database.expenseCategoryDao(),
                app.database.settingsDao()
            ) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(app.database.settingsDao(), app.imageStore) as T

        modelClass.isAssignableFrom(BackupViewModel::class.java) ->
            BackupViewModel(app.backupManager) as T

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

// Usage inside a screen: viewModel<SomeViewModel>(factory = appViewModelFactory())
@Composable
fun appViewModelFactory(): AppViewModelFactory {
    val app = LocalContext.current.applicationContext as CafeCashierApp
    return remember(app) { AppViewModelFactory(app) }
}
