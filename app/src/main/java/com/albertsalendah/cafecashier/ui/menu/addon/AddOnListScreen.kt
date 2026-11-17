package com.albertsalendah.cafecashier.ui.menu.addon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.NameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOnListScreen() {
    val viewModel = viewModel<AddOnListViewModel>(factory = appViewModelFactory())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val addOns by viewModel.addOns.collectAsStateWithLifecycle()
    val categoryList = categories
    val addOnList = addOns

    // Which dialog is open. Ids (not objects) so the state survives rotation.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.add_ons)) }) },
        floatingActionButton = {
            // Add-ons belong to a category, so there is nothing to add until one exists.
            if (!categoryList.isNullOrEmpty()) {
                ExtendedFloatingActionButton(onClick = { showAdd = true }) {
                    Text(stringResource(R.string.add_add_on))
                }
            }
        }
    ) { innerPadding ->
        when {
            categoryList == null -> Box(
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
            ) { Text(stringResource(R.string.add_category_first_add_ons)) }

            else -> Column(modifier = Modifier.padding(innerPadding)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoryList, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == selectedId,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
                HorizontalDivider()

                when {
                    addOnList == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    addOnList.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.add_ons_empty)) }

                    else -> LazyColumn(
                        // Space at the bottom so the last row isn't hidden behind the button.
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(addOnList, key = { it.id }) { option ->
                            ListItem(
                                headlineContent = { Text(option.name) },
                                modifier = Modifier.clickable { editingId = option.id }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    val categoryId = selectedId
    if (showAdd && categoryId != null) {
        NameDialog(
            title = stringResource(R.string.add_add_on),
            label = stringResource(R.string.add_on_name),
            initialName = "",
            onSave = {
                viewModel.add(categoryId, it)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    addOnList?.firstOrNull { it.id == editingId }?.let { option ->
        NameDialog(
            title = stringResource(R.string.edit_add_on),
            label = stringResource(R.string.add_on_name),
            initialName = option.name,
            onSave = {
                viewModel.rename(option, it)
                editingId = null
            },
            onDismiss = { editingId = null },
            extra = {
                TextButton(onClick = {
                    editingId = null
                    deletingId = option.id
                }) { Text(stringResource(R.string.delete)) }
            }
        )
    }

    // A real delete (there is no hide for add-ons), so confirm first and say what is not affected.
    addOnList?.firstOrNull { it.id == deletingId }?.let { option ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.delete_item_title, option.name)) },
            text = { Text(stringResource(R.string.delete_add_on_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(option)
                    deletingId = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
