package com.albertsalendah.cafecashier.ui.cash.category

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.ExpandableText
import com.albertsalendah.cafecashier.ui.components.NameDialog
import com.albertsalendah.cafecashier.ui.components.ShowHiddenRow

// Manage the categories the expense form offers. Same layout and behaviour as the menu's category
// screen (add, rename, hide, restore, "Show hidden"), without the products rule.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoryListScreen() {
    val viewModel = viewModel<ExpenseCategoryListViewModel>(factory = appViewModelFactory())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenCategories.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val list = categories
    // The switch only exists while something is hidden. If the last hidden item is restored while the
    // switch is on, treat it as off (the stored switch value is left alone).
    val hasHidden = hidden.isNotEmpty()
    val showingHidden = showHidden && hasHidden

    // Which dialog is open. Ids (not objects) so the state survives rotation.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var hidingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.expense_categories)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) {
                Text(stringResource(R.string.add_expense_category))
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
                if (hasHidden) {
                    ShowHiddenRow(
                        checked = showHidden,
                        onCheckedChange = viewModel::setShowHidden,
                        summary = pluralStringResource(
                            R.plurals.hidden_expense_categories_count,
                            hidden.size,
                            hidden.size
                        )
                    )
                    HorizontalDivider()
                }

                if (list.isEmpty() && !showingHidden) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.expense_categories_empty)) }
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

                        if (showingHidden) {
                            item(key = "hidden-header") {
                                Text(
                                    text = stringResource(R.string.hidden_section),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                                )
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
            title = stringResource(R.string.add_expense_category),
            label = stringResource(R.string.expense_category_name),
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
            title = stringResource(R.string.edit_expense_category),
            label = stringResource(R.string.expense_category_name),
            initialName = category.name,
            onSave = {
                viewModel.rename(category, it)
                editingId = null
            },
            onDismiss = { editingId = null },
            // The "Hide category" button and its explanation show only when editing.
            extra = {
                TextButton(onClick = {
                    editingId = null
                    hidingId = category.id
                }) { Text(stringResource(R.string.hide_category)) }
                ExpandableText(
                    shortText = stringResource(R.string.expense_category_hide_helper_short),
                    fullText = stringResource(R.string.expense_category_hide_helper),
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
            text = { Text(stringResource(R.string.expense_category_hide_helper)) },
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
