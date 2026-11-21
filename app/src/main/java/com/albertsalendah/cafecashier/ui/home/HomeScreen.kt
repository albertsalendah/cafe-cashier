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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.bills.BillsScreen
import com.albertsalendah.cafecashier.ui.menu.MenuHomeScreen
import com.albertsalendah.cafecashier.ui.order.OrderScreen

private enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    ORDER(R.string.tab_order, Icons.Filled.ShoppingCart),
    BILLS(R.string.tab_bills, Icons.AutoMirrored.Filled.List),
    MENU(R.string.tab_menu, Icons.Filled.Menu)
}

// The app's home: a bottom bar with Order, Bills and Menu. Every other screen (cart, categories,
// product form...) is pushed on top of it and covers the bar. The ViewModels belong to this
// back-stack entry, so switching tabs keeps each tab's state.
@Composable
fun HomeScreen(
    onOpenCart: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenAddOns: () -> Unit,
    onOpenOrderDiscounts: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.ORDER) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
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
                HomeTab.MENU -> MenuHomeScreen(
                    onOpenCategories = onOpenCategories,
                    onOpenProducts = onOpenProducts,
                    onOpenAddOns = onOpenAddOns,
                    onOpenOrderDiscounts = onOpenOrderDiscounts
                )
            }
        }
    }
}
