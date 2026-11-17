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
import androidx.compose.material3.MaterialTheme
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
import com.albertsalendah.cafecashier.ui.components.ExpandableText
import com.albertsalendah.cafecashier.ui.components.NameDialog
import com.albertsalendah.cafecashier.ui.components.ShowHiddenRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen() {
    val viewModel = viewModel<CategoryListViewModel>(factory = appViewModelFactory())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenCategories.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val list = categories

    // Which dialog is open. Ids (not objects) so the state survives rotation.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var hidingId by rememberSaveable { mutableStateOf<Long?>(null) }

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
        if (list == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.padding(innerPadding)) {
                // Always visible, so staff can find hidden categories even when none are shown yet.
                ShowHiddenRow(checked = showHidden, onCheckedChange = viewModel::setShowHidden)
                HorizontalDivider()

                if (list.isEmpty() && hidden.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.categories_empty)) }
                } else {
                    LazyColumn(
                        // Space at the bottom so the last row isn't hidden behind the button.
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(list, key = { "active-${it.id}" }) { category ->
                            ListItem(
                                headlineContent = { Text(category.name) },
                                modifier = Modifier.clickable { editingId = category.id }
                            )
                            HorizontalDivider()
                        }

                        if (showHidden) {
                            item(key = "hidden-header") {
                                Text(
                                    text = stringResource(R.string.hidden_section),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                                )
                            }
                            if (hidden.isEmpty()) {
                                item(key = "hidden-none") {
                                    Text(
                                        text = stringResource(R.string.no_hidden_categories),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(hidden, key = { "hidden-${it.id}" }) { category ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = category.name,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = {
                                        TextButton(onClick = { viewModel.restore(category) }) {
                                            Text(stringResource(R.string.restore))
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NameDialog(
            title = stringResource(R.string.add_category),
            label = stringResource(R.string.category_name),
            initialName = "",
            onSave = {
                viewModel.add(it)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    list?.firstOrNull { it.id == editingId }?.let { category ->
        NameDialog(
            title = stringResource(R.string.rename_category),
            label = stringResource(R.string.category_name),
            initialName = category.name,
            onSave = {
                viewModel.rename(category, it)
                editingId = null
            },
            onDismiss = { editingId = null },
            // The "Hide from menu" button and its explanation show only when renaming.
            extra = {
                TextButton(onClick = {
                    editingId = null
                    hidingId = category.id
                }) { Text(stringResource(R.string.hide_from_menu)) }
                ExpandableText(
                    shortText = stringResource(R.string.hide_helper_short),
                    fullText = stringResource(R.string.hide_helper),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )
    }

    // The confirmation shows the full explanation, with no "More..." step: it is the last thing
    // read before the change.
    list?.firstOrNull { it.id == hidingId }?.let { category ->
        AlertDialog(
            onDismissRequest = { hidingId = null },
            title = { Text(stringResource(R.string.hide_item_title, category.name)) },
            text = { Text(stringResource(R.string.hide_helper)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archive(category)
                    hidingId = null
                }) { Text(stringResource(R.string.hide)) }
            },
            dismissButton = {
                TextButton(onClick = { hidingId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
