package com.albertsalendah.cafecashier.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.albertsalendah.cafecashier.ui.menu.MenuHomeScreen
import com.albertsalendah.cafecashier.ui.menu.addon.AddOnListScreen
import com.albertsalendah.cafecashier.ui.menu.category.CategoryListScreen
import com.albertsalendah.cafecashier.ui.menu.discount.OrderDiscountListScreen
import com.albertsalendah.cafecashier.ui.menu.product.ProductEditScreen
import com.albertsalendah.cafecashier.ui.menu.product.ProductListScreen

@Composable
fun AppNav() {
    val backStack = rememberNavBackStack(MenuHome)

    NavDisplay(
        backStack = backStack,
        // The ViewModel decorator scopes each screen's ViewModel to its back-stack entry,
        // so it is created when the screen is pushed and cleared when it is popped.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<MenuHome> {
                MenuHomeScreen(
                    onOpenCategories = { backStack.add(CategoryList) },
                    onOpenProducts = { backStack.add(ProductList) },
                    onOpenAddOns = { backStack.add(AddOnList) },
                    onOpenOrderDiscounts = { backStack.add(OrderDiscountList) }
                )
            }
            entry<AddOnList> {
                AddOnListScreen()
            }
            entry<OrderDiscountList> {
                OrderDiscountListScreen()
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
