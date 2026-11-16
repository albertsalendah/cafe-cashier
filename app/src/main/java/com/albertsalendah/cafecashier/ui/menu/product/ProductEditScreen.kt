package com.albertsalendah.cafecashier.ui.menu.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.components.ExpandableText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    productId: Long?,
    initialCategoryId: Long?,
    onDone: () -> Unit
) {
    val app = LocalContext.current.applicationContext as CafeCashierApp
    val viewModel = viewModel<ProductEditViewModel>(
        factory = viewModelFactory {
            initializer {
                ProductEditViewModel(
                    productDao = app.database.productDao(),
                    categoryDao = app.database.categoryDao(),
                    productId = productId,
                    initialCategoryId = initialCategoryId
                )
            }
        }
    )
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryList = categories
    var showHideDialog by rememberSaveable { mutableStateOf(false) }
    var categoryMenuOpen by rememberSaveable { mutableStateOf(false) }

    // The product was removed from under us: nothing to edit, so close.
    LaunchedEffect(viewModel.missing) {
        if (viewModel.missing) onDone()
    }

    // A new product with no category chosen yet starts on the first category.
    LaunchedEffect(categoryList, viewModel.categoryId) {
        if (viewModel.isNew && viewModel.categoryId == null && !categoryList.isNullOrEmpty()) {
            viewModel.onCategoryChange(categoryList.first().id)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (viewModel.isNew) R.string.new_product else R.string.edit_product)
                    )
                }
            )
        }
    ) { innerPadding ->
        when {
            !viewModel.ready || categoryList == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            categoryList.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.add_category_first)) }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category picker: a button showing the current category that opens a menu.
                Box {
                    OutlinedButton(
                        onClick = { categoryMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val current = categoryList.firstOrNull { it.id == viewModel.categoryId }
                        Text(
                            stringResource(R.string.category_label) + ": " + (current?.name ?: "-")
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        categoryList.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    viewModel.onCategoryChange(category.id)
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = viewModel.priceText,
                    onValueChange = viewModel::onPriceChange,
                    singleLine = true,
                    prefix = { Text("Rp ") },
                    label = { Text(stringResource(R.string.price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { viewModel.save(onDone) },
                    enabled = viewModel.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save)) }

                TextButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.cancel)) }

                // Only an existing product can be hidden.
                if (!viewModel.isNew) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showHideDialog = true }) {
                            Text(stringResource(R.string.hide_from_menu))
                        }
                        ExpandableText(
                            shortText = stringResource(R.string.hide_helper_short),
                            fullText = stringResource(R.string.hide_helper)
                        )
                    }
                }
            }
        }
    }

    if (showHideDialog) {
        AlertDialog(
            onDismissRequest = { showHideDialog = false },
            title = { Text(stringResource(R.string.hide_item_title, viewModel.name)) },
            text = { Text(stringResource(R.string.hide_helper)) },
            confirmButton = {
                TextButton(onClick = {
                    showHideDialog = false
                    viewModel.hide(onDone)
                }) { Text(stringResource(R.string.hide)) }
            },
            dismissButton = {
                TextButton(onClick = { showHideDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
