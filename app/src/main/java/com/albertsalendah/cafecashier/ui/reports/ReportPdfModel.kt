package com.albertsalendah.cafecashier.ui.reports

import android.content.Context
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.domain.PdfDoc
import com.albertsalendah.cafecashier.domain.PdfRow
import com.albertsalendah.cafecashier.domain.PdfSection
import com.albertsalendah.cafecashier.ui.formatEpochDay
import com.albertsalendah.cafecashier.ui.formatRupiah

const val PDF_TOP_PRODUCTS = 20

// "16 Nov 2026" for one day, "10 Nov 2026 – 16 Nov 2026" for a span.
fun rangeText(context: Context, from: Long, to: Long): String =
    if (from == to) formatEpochDay(from)
    else context.getString(R.string.report_range_span, formatEpochDay(from), formatEpochDay(to))

// The same sections as the Reports screen, worded for print. The PDF lists the top 20 products.
fun buildReportPdfDoc(context: Context, ui: ReportUiState): PdfDoc {
    val report = ui.report
    val s = report.summary
    val e = report.expenses
    val sections = mutableListOf<PdfSection>()

    val sales = mutableListOf(
        PdfRow(context.getString(R.string.report_sale_count), s.saleCount.toString()),
        PdfRow(context.getString(R.string.report_gross), formatRupiah(s.gross))
    )
    if (s.productDiscounts > 0) {
        sales += PdfRow(context.getString(R.string.product_discounts), "-" + formatRupiah(s.productDiscounts))
    }
    if (s.orderDiscounts > 0) {
        sales += PdfRow(context.getString(R.string.order_discount_label), "-" + formatRupiah(s.orderDiscounts))
    }
    if (s.tax > 0) sales += PdfRow(context.getString(R.string.tax), formatRupiah(s.tax))
    if (s.rounding != 0L) {
        val sign = if (s.rounding > 0) "+" else "-"
        sales += PdfRow(context.getString(R.string.rounding), sign + formatRupiah(kotlin.math.abs(s.rounding)))
    }
    sales += PdfRow(context.getString(R.string.report_total), formatRupiah(s.total), bold = true)
    sales += PdfRow(context.getString(R.string.report_average), formatRupiah(s.averageSale))
    sections += PdfSection(context.getString(R.string.report_sales_section), sales)

    val expenses = mutableListOf(
        PdfRow(context.getString(R.string.expense_pool_daily), formatRupiah(e.dailyCash)),
        PdfRow(context.getString(R.string.expense_pool_operational), formatRupiah(e.operational)),
        PdfRow(context.getString(R.string.report_total), formatRupiah(e.total), bold = true)
    )
    e.byCategory.forEach { expenses += PdfRow("  " + it.categoryName, formatRupiah(it.total)) }
    sections += PdfSection(context.getString(R.string.report_expenses_section), expenses)

    sections += PdfSection(
        context.getString(R.string.report_balance),
        listOf(PdfRow(context.getString(R.string.report_balance_short), formatRupiah(report.balance), bold = true))
    )

    if (report.voided.count > 0) {
        sections += PdfSection(
            context.getString(R.string.report_voided_section),
            listOf(
                PdfRow(
                    context.getString(R.string.report_voided_count, report.voided.count),
                    formatRupiah(report.voided.total)
                )
            )
        )
    }

    if (report.range.dayCount > 1 && report.perDay.isNotEmpty()) {
        sections += PdfSection(
            context.getString(R.string.report_per_day),
            report.perDay.map {
                PdfRow(
                    formatEpochDay(it.day) + "  (" + context.getString(R.string.report_sales_n, it.saleCount) + ")",
                    formatRupiah(it.total)
                )
            }
        )
    }

    if (report.products.isNotEmpty()) {
        sections += PdfSection(
            context.getString(R.string.report_top_products),
            report.products.take(PDF_TOP_PRODUCTS).mapIndexed { i, p ->
                PdfRow("${i + 1}. ${p.name}  ×${p.quantity}", formatRupiah(p.revenue))
            }
        )
    }

    val title = ui.storeName.ifEmpty { context.getString(R.string.report_pdf_title) }
    val subtitle = buildString {
        if (ui.storeName.isNotEmpty()) append(context.getString(R.string.report_pdf_title)).append(" · ")
        append(rangeText(context, report.range.fromDay, report.range.toDay))
    }
    return PdfDoc(title, subtitle, sections)
}
