package com.albertsalendah.cafecashier.ui.menu.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.appViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen() {
    val viewModel = viewModel<CategoryListViewModel>(factory = appViewModelFactory())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val list = categories

    // Which dialog is open. Ids (not objects) so the state survives rotation.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archivingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val blockedText = stringResource(R.string.category_archive_blocked)
    // Clear the message only after the snackbar has been shown (changing the key would cancel it).
    LaunchedEffect(message) {
        if (message == CategoryMessage.ArchiveBlockedHasProducts) {
            snackbarHostState.showSnackbar(blockedText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.categories)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) {
                Text(stringResource(R.string.add_category))
            }
        }
    ) { innerPadding ->
        when {
            list == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.categories_empty)) }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding),
                // Space at the bottom so the last row isn't hidden behind the button.
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(list, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        modifier = Modifier.clickable { editingId = category.id }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        CategoryNameDialog(
            title = stringResource(R.string.add_category),
            initialName = "",
            onSave = {
                viewModel.add(it)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    list?.firstOrNull { it.id == editingId }?.let { category ->
        CategoryNameDialog(
            title = stringResource(R.string.rename_category),
            initialName = category.name,
            onSave = {
                viewModel.rename(category, it)
                editingId = null
            },
            onDismiss = { editingId = null },
            onArchive = {
                editingId = null
                archivingId = category.id
            }
        )
    }

    list?.firstOrNull { it.id == archivingId }?.let { category ->
        AlertDialog(
            onDismissRequest = { archivingId = null },
            title = { Text(stringResource(R.string.archive_category_title, category.name)) },
            text = { Text(stringResource(R.string.archive_category_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archive(category)
                    archivingId = null
                }) { Text(stringResource(R.string.archive)) }
            },
            dismissButton = {
                TextButton(onClick = { archivingId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// One dialog for both add and rename. The Archive button shows only when onArchive is given (rename).
@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onArchive: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.category_name)) }
                )
                if (onArchive != null) {
                    TextButton(onClick = onArchive) { Text(stringResource(R.string.archive)) }
                }
            }
        },
        confirmButton = {
            // Blank names are rejected by keeping Save disabled.
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
