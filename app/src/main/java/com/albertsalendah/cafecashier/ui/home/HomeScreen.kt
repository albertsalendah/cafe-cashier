package com.albertsalendah.cafecashier.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.domain.AppArea
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.domain.UserSummary
import com.albertsalendah.cafecashier.domain.canAccess
import com.albertsalendah.cafecashier.ui.bills.BillsScreen
import com.albertsalendah.cafecashier.ui.cash.CashScreen
import com.albertsalendah.cafecashier.ui.menu.MenuHomeScreen
import com.albertsalendah.cafecashier.ui.order.OrderScreen
import com.albertsalendah.cafecashier.ui.sales.SalesScreen

// The Cash and Sales tabs' icons are the text "Rp" and "Σ": the app only has the core Material icon set,
// which has no money or sum icon.
// Each tab belongs to an area; with staff accounts on, a tab shows only if the user's role may open it
// (staff: Order and Bills; Admin: all).
private enum class HomeTab(
    val area: AppArea,
    @StringRes val label: Int,
    val icon: @Composable () -> Unit
) {
    ORDER(AppArea.ORDER, R.string.tab_order, { Icon(Icons.Filled.ShoppingCart, contentDescription = null) }),
    BILLS(AppArea.BILLS, R.string.tab_bills, { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }),
    CASH(AppArea.CASH, R.string.tab_cash, { Text("Rp", fontWeight = FontWeight.Bold) }),
    SALES(AppArea.SALES, R.string.tab_sales, { Text("Σ", fontWeight = FontWeight.Bold) }),
    MENU(AppArea.MENU, R.string.tab_menu, { Icon(Icons.Filled.Menu, contentDescription = null) })
}

// The app's home: a bottom bar with Order, Bills, Cash, Sales and Menu. Every other screen (cart, categories,
// product form...) is pushed on top of it and covers the bar. The ViewModels belong to this
// back-stack entry, so switching tabs keeps each tab's state.
// [currentUser] is null while staff accounts are off (everything is shown, no Lock button). With
// accounts on, a strip above the bar shows who is signed in and the Lock button.
@Composable
fun HomeScreen(
    currentUser: UserSummary?,
    onLock: () -> Unit,
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
    val role = currentUser?.role ?: UserRole.ADMIN
    val tabs = HomeTab.entries.filter { canAccess(role, it.area) }
    var selected by rememberSaveable { mutableStateOf(HomeTab.ORDER) }
    // The saved tab may belong to someone with more rights (or after a role change): never show it then.
    val tab = if (selected in tabs) selected else tabs.firstOrNull() ?: HomeTab.ORDER

    Scaffold(
        bottomBar = {
            Column {
                if (currentUser != null) SessionStrip(currentUser, onLock)
                NavigationBar {
                    tabs.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { selected = entry },
                            icon = entry.icon,
                            label = { Text(stringResource(entry.label)) }
                        )
                    }
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

// Who is signed in, and the Lock button. Lives above the navigation bar so every tab shows it.
@Composable
private fun SessionStrip(user: UserSummary, onLock: () -> Unit) {
    val role = stringResource(if (user.role == UserRole.ADMIN) R.string.user_role_admin else R.string.user_role_staff)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.session_user_line, user.name, role),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onLock) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.lock_button))
        }
    }
}
