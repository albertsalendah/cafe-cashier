package com.albertsalendah.cafecashier.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.bills.BillsScreen
import com.albertsalendah.cafecashier.ui.cash.CashScreen
import com.albertsalendah.cafecashier.ui.menu.MenuHomeScreen
import com.albertsalendah.cafecashier.ui.order.OrderScreen
import com.albertsalendah.cafecashier.ui.sales.SalesScreen

// The Cash and Sales tabs' icons are the text "Rp" and "Σ": the app only has the core Material icon set,
// which has no money or sum icon.
private enum class HomeTab(@StringRes val label: Int, val icon: @Composable () -> Unit) {
    ORDER(R.string.tab_order, { Icon(Icons.Filled.ShoppingCart, contentDescription = null) }),
    BILLS(R.string.tab_bills, { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }),
    CASH(R.string.tab_cash, { Text("Rp", fontWeight = FontWeight.Bold) }),
    SALES(R.string.tab_sales, { Text("Σ", fontWeight = FontWeight.Bold) }),
    MENU(R.string.tab_menu, { Icon(Icons.Filled.Menu, contentDescription = null) })
}

// The app's home: a bottom bar with Order, Bills, Cash, Sales and Menu. Every other screen (cart, categories,
// product form...) is pushed on top of it and covers the bar. The ViewModels belong to this
// back-stack entry, so switching tabs keeps each tab's state.
@Composable
fun HomeScreen(
    onOpenCart: () -> Unit,
    onOpenExpenseCategories: () -> Unit,
    onOpenSale: (Long) -> Unit,
    onOpenReports: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenAddOns: () -> Unit,
    onOpenOrderDiscounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenStaffAccounts: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.ORDER) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = entry.icon,
                        label = { Text(stringResource(entry.label)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // The tabs have their own Scaffold and top bar; tell them the insets are already handled here.
        Box(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            when (tab) {
                HomeTab.ORDER -> OrderScreen(onOpenCart = onOpenCart)
                HomeTab.BILLS -> BillsScreen(onOpenCart = onOpenCart)
                HomeTab.CASH -> CashScreen(onOpenExpenseCategories = onOpenExpenseCategories)
                HomeTab.SALES -> SalesScreen(onOpenSale = onOpenSale, onOpenReports = onOpenReports)
                HomeTab.MENU -> MenuHomeScreen(
                    onOpenCategories = onOpenCategories,
                    onOpenProducts = onOpenProducts,
                    onOpenAddOns = onOpenAddOns,
                    onOpenOrderDiscounts = onOpenOrderDiscounts,
                    onOpenSettings = onOpenSettings,
                    onOpenBackup = onOpenBackup,
                    onOpenStaffAccounts = onOpenStaffAccounts
                )
            }
        }
    }
}
