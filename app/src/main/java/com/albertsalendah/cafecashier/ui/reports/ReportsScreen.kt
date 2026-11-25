package com.albertsalendah.cafecashier.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.MIME_CSV
import com.albertsalendah.cafecashier.data.MIME_PDF
import com.albertsalendah.cafecashier.data.ReportExporter
import com.albertsalendah.cafecashier.domain.RangePreset
import com.albertsalendah.cafecashier.domain.expensesCsv
import com.albertsalendah.cafecashier.domain.productsCsv
import com.albertsalendah.cafecashier.domain.salesCsv
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.DayPickerDialog
import com.albertsalendah.cafecashier.ui.formatEpochDay
import com.albertsalendah.cafecashier.ui.formatRupiah
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

private const val TOP_PRODUCTS_ON_SCREEN = 10

private enum class PickingEnd { FROM, TO }

// Reports for a range of days: what was sold, what was spent, the days and the best sellers, with the
// same figures exportable as CSV files and a PDF. Voided sales are never part of the numbers.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val viewModel = viewModel<ReportsViewModel>(factory = appViewModelFactory())
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only use the state when it was built for the range being shown (see ReportsViewModel.state).
    val ui = state?.takeIf { it.range == range }

    val context = LocalContext.current
    val exporter = remember(context) { ReportExporter(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<PickingEnd?>(null) }
    val exportFailed = stringResource(R.string.export_failed)
    val shareTitle = stringResource(R.string.export_share_title)

    // Writes one file off the main thread, then opens the share sheet.
    fun export(mime: String, makeFile: suspend () -> File) {
        if (exporting) return
        exporting = true
        scope.launch {
            var failed = false
            try {
                exporter.share(makeFile(), mime, shareTitle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                exporting = false
            }
            // After the buttons are enabled again: showSnackbar waits until the message is gone.
            if (failed) snackbarHostState.showSnackbar(exportFailed)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            RangePicker(
                preset = preset,
                fromDay = range.fromDay,
                toDay = range.toDay,
                onPreset = viewModel::selectPreset,
                onPickFrom = { picking = PickingEnd.FROM },
                onPickTo = { picking = PickingEnd.TO }
            )
            HorizontalDivider()

            if (ui == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val report = ui.report
                val s = report.summary
                val e = report.expenses
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ---- Sales ----
                    item(key = "sales") {
                        Card(stringResource(R.string.report_sales_section)) {
                            Text(
                                text = formatRupiah(s.total),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            if (s.saleCount == 0) {
                                Text(
                                    text = stringResource(R.string.report_no_sales),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Line(stringResource(R.string.report_sale_count), s.saleCount.toString())
                                Line(stringResource(R.string.report_average), formatRupiah(s.averageSale))
                                Line(stringResource(R.string.report_gross), formatRupiah(s.gross))
                                if (s.productDiscounts > 0) {
                                    Line(stringResource(R.string.product_discounts), "-" + formatRupiah(s.productDiscounts))
                                }
                                if (s.orderDiscounts > 0) {
                                    Line(stringResource(R.string.order_discount_label), "-" + formatRupiah(s.orderDiscounts))
                                }
                                if (s.tax > 0) Line(stringResource(R.string.tax), formatRupiah(s.tax))
                                if (s.rounding != 0L) {
                                    val sign = if (s.rounding > 0) "+" else "-"
                                    Line(stringResource(R.string.rounding), sign + formatRupiah(kotlin.math.abs(s.rounding)))
                                }
                            }
                            if (report.voided.count > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.report_voided_note,
                                        pluralStringResource(
                                            R.plurals.sales_count,
                                            report.voided.count,
                                            report.voided.count
                                        ),
                                        formatRupiah(report.voided.total)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // ---- Expenses ----
                    item(key = "expenses") {
                        Card(stringResource(R.string.report_expenses_section)) {
                            Text(
                                text = formatRupiah(e.total),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Line(stringResource(R.string.expense_pool_daily), formatRupiah(e.dailyCash))
                            Line(stringResource(R.string.expense_pool_operational), formatRupiah(e.operational))
                            if (e.byCategory.isNotEmpty()) {
                                HorizontalDivider()
                                e.byCategory.forEach { Line(it.categoryName, formatRupiah(it.total)) }
                            }
                        }
                    }

                    // ---- Balance ----
                    item(key = "balance") {
                        Card(stringResource(R.string.report_balance)) {
                            Line(
                                label = stringResource(R.string.report_balance_short),
                                value = formatRupiah(report.balance),
                                bold = true,
                                valueColor = if (report.balance < 0) MaterialTheme.colorScheme.error else null
                            )
                            Text(
                                text = stringResource(R.string.report_balance_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ---- By day (only for more than one day) ----
                    if (report.range.dayCount > 1 && report.perDay.isNotEmpty()) {
                        item(key = "days") {
                            Card(stringResource(R.string.report_per_day)) {
                                report.perDay.forEach {
                                    Line(
                                        label = formatEpochDay(it.day) + " · " +
                                            pluralStringResource(R.plurals.sales_count, it.saleCount, it.saleCount),
                                        value = formatRupiah(it.total)
                                    )
                                }
                            }
                        }
                    }

                    // ---- Top products ----
                    item(key = "products") {
                        Card(stringResource(R.string.report_top_products)) {
                            if (report.products.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.report_products_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                report.products.take(TOP_PRODUCTS_ON_SCREEN).forEachIndexed { i, p ->
                                    Line(
                                        label = "${i + 1}. ${p.name}  ×${p.quantity}",
                                        value = formatRupiah(p.revenue)
                                    )
                                }
                                if (report.products.size > TOP_PRODUCTS_ON_SCREEN) {
                                    Text(
                                        text = stringResource(R.string.report_products_more_in_csv),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // ---- Export ----
                    item(key = "export") {
                        Card(stringResource(R.string.report_export)) {
                            Button(
                                onClick = {
                                    export(MIME_PDF) {
                                        exporter.writePdf(
                                            fileName("report", ui, "pdf"),
                                            buildReportPdfDoc(context, ui)
                                        )
                                    }
                                },
                                enabled = !exporting,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.export_pdf)) }
                            OutlinedButton(
                                onClick = {
                                    export(MIME_CSV) {
                                        exporter.writeCsv(
                                            fileName("sales", ui, "csv"),
                                            salesCsv(ui.paidSales + ui.voidedSales, ui.zone)
                                        )
                                    }
                                },
                                enabled = !exporting,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.export_sales_csv)) }
                            OutlinedButton(
                                onClick = {
                                    export(MIME_CSV) {
                                        exporter.writeCsv(
                                            fileName("products", ui, "csv"),
                                            productsCsv(report.products)
                                        )
                                    }
                                },
                                enabled = !exporting,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.export_products_csv)) }
                            OutlinedButton(
                                onClick = {
                                    export(MIME_CSV) {
                                        exporter.writeCsv(
                                            fileName("expenses", ui, "csv"),
                                            expensesCsv(ui.expenses, ui.categoryNames)
                                        )
                                    }
                                },
                                enabled = !exporting,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.export_expenses_csv)) }
                        }
                    }
                }
            }
        }
    }

    picking?.let { end ->
        DayPickerDialog(
            initialDay = if (end == PickingEnd.FROM) range.fromDay else range.toDay,
            onPicked = {
                if (end == PickingEnd.FROM) viewModel.setFrom(it) else viewModel.setTo(it)
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

// "sales_2026-11-01_to_2026-11-24.csv" (one date when the range is a single day).
private fun fileName(kind: String, ui: ReportUiState, extension: String): String {
    val from = LocalDate.ofEpochDay(ui.range.fromDay)
    val to = LocalDate.ofEpochDay(ui.range.toDay)
    val dates = if (from == to) "$from" else "${from}_to_$to"
    return "${kind}_$dates.$extension"
}

@Composable
private fun RangePicker(
    preset: RangePreset,
    fromDay: Long,
    toDay: Long,
    onPreset: (RangePreset) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (entry in RangePreset.entries) {
                FilterChip(
                    selected = preset == entry,
                    onClick = { onPreset(entry) },
                    label = { Text(stringResource(presetLabel(entry))) }
                )
            }
        }
        if (preset == RangePreset.CUSTOM) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onPickFrom, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.range_from, formatEpochDay(fromDay)))
                }
                OutlinedButton(onClick = onPickTo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.range_to, formatEpochDay(toDay)))
                }
            }
        } else {
            Text(
                text = if (fromDay == toDay) formatEpochDay(fromDay)
                else stringResource(R.string.report_range_span, formatEpochDay(fromDay), formatEpochDay(toDay)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun presetLabel(preset: RangePreset): Int = when (preset) {
    RangePreset.TODAY -> R.string.range_today
    RangePreset.YESTERDAY -> R.string.range_yesterday
    RangePreset.THIS_WEEK -> R.string.range_this_week
    RangePreset.THIS_MONTH -> R.string.range_this_month
    RangePreset.CUSTOM -> R.string.range_custom
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun Line(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row {
        Text(label, style = style, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(
            text = value,
            style = style,
            color = valueColor ?: androidx.compose.ui.graphics.Color.Unspecified
        )
    }
}
