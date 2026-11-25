package com.albertsalendah.cafecashier.ui.cash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.ExpensePool
import com.albertsalendah.cafecashier.domain.DayClosing
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.DayBar
import com.albertsalendah.cafecashier.ui.components.DayPickerDialog
import com.albertsalendah.cafecashier.ui.formatRupiah

// The Cash tab: pick a day, set its opening cash, add expenses, and see the closing summary
// (opening + cash sales - daily-cash expenses). Everything on it belongs to the day being shown.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(onOpenExpenseCategories: () -> Unit) {
    val viewModel = viewModel<CashViewModel>(factory = appViewModelFactory())
    val day by viewModel.day.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    // Only use the state when it was built for the day being shown (see CashViewModel.state).
    val ui = state?.takeIf { it.day == day }

    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    var showOpeningDialog by rememberSaveable { mutableStateOf(false) }
    var showAddExpense by rememberSaveable { mutableStateOf(false) }
    // The expense being cancelled: its id (not the object) so the state survives rotation.
    var cancellingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cash_title)) },
                actions = {
                    TextButton(onClick = onOpenExpenseCategories) {
                        Text(stringResource(R.string.expense_categories))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddExpense = true }) {
                Text(stringResource(R.string.add_expense))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            DayBar(
                day = day,
                today = viewModel.today(),
                onPrevious = viewModel::previousDay,
                onNext = viewModel::nextDay,
                onPick = { showDayPicker = true },
                onToday = viewModel::goToToday
            )
            HorizontalDivider()

            if (ui == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    // Space at the bottom so the last row isn't hidden behind the button.
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item(key = "opening") {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.opening_cash)) },
                            supportingContent = if (ui.openingCash == null) {
                                ({ Text(stringResource(R.string.opening_cash_not_set)) })
                            } else null,
                            trailingContent = {
                                Text(ui.openingCash?.let(::formatRupiah) ?: "-")
                            },
                            modifier = Modifier.clickable { showOpeningDialog = true }
                        )
                        HorizontalDivider()
                    }

                    item(key = "closing") {
                        ClosingCard(
                            closing = ui.closing,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    item(key = "expenses-header") {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                            Text(
                                text = stringResource(R.string.expenses_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (ui.expenses.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.expenses_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (ui.expenses.isEmpty()) {
                        item(key = "expenses-empty") {
                            Text(
                                text = stringResource(R.string.expenses_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(ui.expenses, key = { "expense-${it.expense.id}" }) { row ->
                            ExpenseItem(row = row, onClick = { cancellingId = row.expense.id })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showDayPicker) {
        DayPickerDialog(
            initialDay = day,
            onPicked = {
                viewModel.selectDay(it)
                showDayPicker = false
            },
            onDismiss = { showDayPicker = false }
        )
    }

    if (showOpeningDialog) {
        OpeningCashDialog(
            initial = ui?.openingCash,
            onSave = {
                viewModel.setOpeningCash(it)
                showOpeningDialog = false
            },
            onDismiss = { showOpeningDialog = false }
        )
    }

    if (showAddExpense) {
        // Wait for the categories to load, so the form doesn't flash "add a category first".
        categories?.let { list ->
            ExpenseDialog(
                categories = list,
                onSave = { categoryId, name, unitAmount, quantity, pool ->
                    viewModel.addExpense(categoryId, name, unitAmount, quantity, pool)
                    showAddExpense = false
                },
                onDismiss = { showAddExpense = false }
            )
        }
    }

    // Tapping an expense asks before cancelling it. A cancelled expense can't be tapped.
    ui?.expenses?.firstOrNull { it.expense.id == cancellingId && !it.expense.cancelled }?.let { row ->
        AlertDialog(
            onDismissRequest = { cancellingId = null },
            title = { Text(stringResource(R.string.expense_cancel_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.expense_cancel_message,
                        row.expense.name,
                        formatRupiah(row.expense.total)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelExpense(row.expense)
                    cancellingId = null
                }) { Text(stringResource(R.string.expense_cancel_action)) }
            },
            dismissButton = {
                TextButton(onClick = { cancellingId = null }) {
                    Text(stringResource(R.string.expense_keep))
                }
            }
        )
    }
}

// opening + cash sales - daily-cash expenses = expected cash in the drawer. The expected amount turns
// red when it is below zero. Operational expenses are listed apart: they don't touch the drawer.
@Composable
private fun ClosingCard(closing: DayClosing, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.closing_title),
                style = MaterialTheme.typography.titleMedium
            )
            AmountRow(stringResource(R.string.opening_cash), formatRupiah(closing.openingCash))
            AmountRow(stringResource(R.string.closing_cash_sales), "+ " + formatRupiah(closing.cashSales))
            AmountRow(
                stringResource(R.string.closing_daily_expenses),
                "− " + formatRupiah(closing.dailyCashExpenses)
            )
            HorizontalDivider()
            AmountRow(
                label = stringResource(R.string.closing_expected),
                value = formatRupiah(closing.expectedCash),
                bold = true,
                color = if (closing.expectedCash < 0) MaterialTheme.colorScheme.error else Color.Unspecified
            )
            if (!closing.openingCashSet) {
                Text(
                    text = stringResource(R.string.closing_opening_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AmountRow(
                label = stringResource(R.string.closing_operational),
                value = formatRupiah(closing.operationalExpenses),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AmountRow(
    label: String,
    value: String,
    bold: Boolean = false,
    color: Color = Color.Unspecified
) {
    val weight = if (bold) FontWeight.Bold else null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = weight,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Text(text = value, color = color, fontWeight = weight)
    }
}

// One expense: name, "Category · Pool (· 3 × Rp 5.000) (· Cancelled)" and the total. A cancelled
// expense stays in the list with its name and total struck through, and can't be tapped.
@Composable
private fun ExpenseItem(row: ExpenseRow, onClick: () -> Unit) {
    val expense = row.expense
    val cancelled = expense.cancelled
    val strike = if (cancelled) TextDecoration.LineThrough else TextDecoration.None
    val textColor = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified

    val poolLabel = stringResource(
        if (expense.pool == ExpensePool.DAILY_CASH) R.string.expense_pool_daily else R.string.expense_pool_operational
    )
    val quantityText = stringResource(R.string.line_quantity_price, expense.quantity, formatRupiah(expense.unitAmount))
    val cancelledText = stringResource(R.string.expense_cancelled)
    val detail = listOfNotNull(
        row.categoryName.ifEmpty { null },
        poolLabel,
        if (expense.quantity > 1) quantityText else null,
        if (cancelled) cancelledText else null
    ).joinToString(" · ")

    ListItem(
        headlineContent = { Text(expense.name, textDecoration = strike, color = textColor) },
        supportingContent = { Text(detail) },
        trailingContent = { Text(formatRupiah(expense.total), textDecoration = strike, color = textColor) },
        modifier = if (cancelled) Modifier else Modifier.clickable(onClick = onClick)
    )
}
