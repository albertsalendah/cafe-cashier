package com.albertsalendah.cafecashier.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.appViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuHomeScreen(
    onOpenCategories: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenAddOns: () -> Unit,
    onOpenOrderDiscounts: () -> Unit
) {
    val viewModel = viewModel<MenuHomeViewModel>(factory = appViewModelFactory())
    val canImport by viewModel.canImport.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showImportConfirm by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val doneText = stringResource(R.string.import_old_menu_done)
    val failedText = stringResource(R.string.import_old_menu_failed)
    // Clear the message only after the snackbar has been shown (changing the key would cancel it).
    LaunchedEffect(message) {
        val text = when (message) {
            MenuHomeMessage.ImportDone -> doneText
            MenuHomeMessage.ImportFailed -> failedText
            null -> null
        }
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.menu_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // One row per menu area. The import row comes first, and only while the menu is empty.
        Column(modifier = Modifier.padding(innerPadding)) {
            if (canImport) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.import_old_menu)) },
                    supportingContent = { Text(stringResource(R.string.import_old_menu_hint)) },
                    modifier = Modifier.clickable { showImportConfirm = true }
                )
                HorizontalDivider()
            }
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
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.import_old_menu_title)) },
            text = { Text(stringResource(R.string.import_old_menu_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    viewModel.importOldMenu()
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
