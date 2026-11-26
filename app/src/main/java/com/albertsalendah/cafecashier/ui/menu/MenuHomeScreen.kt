package com.albertsalendah.cafecashier.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.albertsalendah.cafecashier.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuHomeScreen(
    onOpenCategories: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenAddOns: () -> Unit,
    onOpenOrderDiscounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.menu_title)) }) }
    ) { innerPadding ->
        // One row per menu area.
        Column(modifier = Modifier.padding(innerPadding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.categories)) },
                modifier = Modifier.clickable(onClick = onOpenCategories)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.products)) },
                modifier = Modifier.clickable(onClick = onOpenProducts)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_ons)) },
                modifier = Modifier.clickable(onClick = onOpenAddOns)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.order_discounts)) },
                modifier = Modifier.clickable(onClick = onOpenOrderDiscounts)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                supportingContent = { Text(stringResource(R.string.settings_hint)) },
                modifier = Modifier.clickable(onClick = onOpenSettings)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_title)) },
                supportingContent = { Text(stringResource(R.string.backup_menu_hint)) },
                modifier = Modifier.clickable(onClick = onOpenBackup)
            )
            HorizontalDivider()
        }
    }
}
