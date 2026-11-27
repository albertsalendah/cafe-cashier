package com.albertsalendah.cafecashier.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.albertsalendah.cafecashier.domain.UserSummary
import com.albertsalendah.cafecashier.ui.backup.BackupScreen
import com.albertsalendah.cafecashier.ui.cash.category.ExpenseCategoryListScreen
import com.albertsalendah.cafecashier.ui.home.HomeScreen
import com.albertsalendah.cafecashier.ui.menu.addon.AddOnListScreen
import com.albertsalendah.cafecashier.ui.menu.category.CategoryListScreen
import com.albertsalendah.cafecashier.ui.menu.discount.OrderDiscountListScreen
import com.albertsalendah.cafecashier.ui.menu.product.ProductEditScreen
import com.albertsalendah.cafecashier.ui.menu.product.ProductListScreen
import com.albertsalendah.cafecashier.ui.order.CartScreen
import com.albertsalendah.cafecashier.ui.order.CheckoutScreen
import com.albertsalendah.cafecashier.ui.reports.ReportsScreen
import com.albertsalendah.cafecashier.ui.sales.SaleDetailScreen
import com.albertsalendah.cafecashier.ui.settings.SettingsScreen
import com.albertsalendah.cafecashier.ui.users.StaffAccountsScreen

// [currentUser] is the signed-in user, or null while staff accounts are off (then everything is open).
// [onLock] is the Lock button on the Home screen. The whole stack is rebuilt by AppGate when the role changes.
@Composable
fun AppNav(currentUser: UserSummary?, onLock: () -> Unit) {
    val backStack = rememberNavBackStack(Home)
    // Back to the Home screen, whatever is on top (Home is always the first entry). Safe to call twice.
    val popToHome = {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        // The ViewModel decorator scopes each screen's ViewModel to its back-stack entry,
        // so it is created when the screen is pushed and cleared when it is popped.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(
                    currentUser = currentUser,
                    onLock = onLock,
                    onOpenCart = { backStack.add(Cart) },
                    onOpenExpenseCategories = { backStack.add(ExpenseCategoryList) },
                    onOpenSale = { saleId -> backStack.add(SaleDetail(saleId)) },
                    onOpenReports = { backStack.add(Reports) },
                    onOpenCategories = { backStack.add(CategoryList) },
                    onOpenProducts = { backStack.add(ProductList) },
                    onOpenAddOns = { backStack.add(AddOnList) },
                    onOpenOrderDiscounts = { backStack.add(OrderDiscountList) },
                    onOpenSettings = { backStack.add(Settings) },
                    onOpenBackup = { backStack.add(BackupRestore) },
                    onOpenStaffAccounts = { backStack.add(StaffAccounts) }
                )
            }
            entry<Cart> {
                CartScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onCheckout = { backStack.add(Checkout) }
                )
            }
            entry<Checkout> {
                CheckoutScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onDone = { popToHome() }
                )
            }
            entry<SaleDetail> { key ->
                SaleDetailScreen(saleId = key.saleId, onBack = { backStack.removeLastOrNull() })
            }
            entry<Reports> {
                ReportsScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Settings> {
                SettingsScreen(onDone = { backStack.removeLastOrNull() })
            }
            entry<BackupRestore> {
                BackupScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<StaffAccounts> {
                StaffAccountsScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<AddOnList> {
                AddOnListScreen()
            }
            entry<OrderDiscountList> {
                OrderDiscountListScreen()
            }
            entry<ExpenseCategoryList> {
                ExpenseCategoryListScreen()
            }
            entry<CategoryList> {
                CategoryListScreen()
            }
            entry<ProductList> {
                ProductListScreen(
                    onAddProduct = { categoryId -> backStack.add(ProductEdit(categoryId = categoryId)) },
                    onEditProduct = { productId -> backStack.add(ProductEdit(productId = productId)) }
                )
            }
            entry<ProductEdit> { key ->
                ProductEditScreen(
                    productId = key.productId,
                    initialCategoryId = key.categoryId,
                    onDone = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
