package com.albertsalendah.cafecashier.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.domain.HistoryStatus
import com.albertsalendah.cafecashier.domain.HistorySummary
import com.albertsalendah.cafecashier.domain.historyStatus
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.DayBar
import com.albertsalendah.cafecashier.ui.components.DayPickerDialog
import com.albertsalendah.cafecashier.ui.formatBillTime
import com.albertsalendah.cafecashier.ui.formatRupiah
import com.albertsalendah.cafecashier.ui.order.orderTypeLabel

// The Sales tab: every sale paid on the chosen day, newest first, with the day's total on top.
// Voided sales and bills cancelled before payment stay in the list, labelled and crossed out.
// Tap a sale for its detail, where a paid sale can be voided.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(onOpenSale: (Long) -> Unit, onOpenReports: () -> Unit) {
    val viewModel = viewModel<SalesViewModel>(factory = appViewModelFactory())
    val day by viewModel.day.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only use the state when it was built for the day being shown (see SalesViewModel.state).
    val ui = state?.takeIf { it.day == day }
    var showDayPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sales_title)) },
                actions = {
                    TextButton(onClick = onOpenReports) { Text(stringResource(R.string.reports_title)) }
                }
            )
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
            } else if (ui.sales.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.sales_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    item(key = "summary") {
                        SummaryCard(ui.summary, modifier = Modifier.padding(16.dp))
                    }
                    items(ui.sales, key = { it.id }) { sale ->
                        SaleRow(sale, onClick = { onOpenSale(sale.id) })
                        HorizontalDivider()
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
}

@Composable
private fun SummaryCard(summary: HistorySummary, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = formatRupiah(summary.paidTotal),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = pluralStringResource(R.plurals.sales_count, summary.paidCount, summary.paidCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary.voidedCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.sales_voided_count, summary.voidedCount, summary.voidedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (summary.cancelledCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.sales_cancelled_count,
                        summary.cancelledCount,
                        summary.cancelledCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Title, then "Dine in · 14:32 · Voided", total on the right. Voided and cancelled totals are crossed out.
@Composable
private fun SaleRow(sale: Sale, onClick: () -> Unit) {
    val status = historyStatus(sale) ?: return
    val time = formatBillTime(sale.paidAt ?: sale.cancelledAt ?: sale.createdAt)
    val counted = status == HistoryStatus.PAID
    ListItem(
        headlineContent = { Text(saleTitle(sale)) },
        supportingContent = {
            Column {
                Text("${orderTypeLabel(sale.orderType)} · $time")
                if (!counted) {
                    Text(
                        text = historyStatusLabel(status),
                        color = historyStatusColor(status),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text = formatRupiah(sale.total),
                textDecoration = if (counted) null else TextDecoration.LineThrough
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
