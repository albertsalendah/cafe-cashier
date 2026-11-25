package com.albertsalendah.cafecashier.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.albertsalendah.cafecashier.domain.PdfDoc
import java.io.File
import java.io.FileOutputStream

// Draws a PdfDoc onto A4 pages with the platform's PdfDocument (no library). Sections that don't fit
// continue on a new page; a section heading never sits alone at the bottom of a page.
class ReportPdfWriter {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val lineHeight = 17f

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        color = Color.DKGRAY
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
    }
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        color = Color.BLACK
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
    }
    private val rulePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.7f
    }

    fun write(doc: PdfDoc, target: File) {
        val pdf = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun startPage() {
            page?.let { pdf.finishPage(it) }
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin
        }

        // Makes sure [height] more points fit on the page, else starts the next one.
        fun ensure(height: Float) {
            if (page == null || y + height > pageHeight - margin) startPage()
        }

        try {
            startPage()
            page!!.canvas.drawText(fit(doc.title, titlePaint), margin, y + 14f, titlePaint)
            y += 28f
            page!!.canvas.drawText(fit(doc.subtitle, subtitlePaint), margin, y, subtitlePaint)
            y += 24f

            val right = pageWidth - margin
            doc.sections.forEach { section ->
                // Heading plus at least the first two rows together.
                ensure(lineHeight * 3 + 12f)
                page!!.canvas.drawText(section.heading, margin, y + 12f, headingPaint)
                y += lineHeight + 4f
                page!!.canvas.drawLine(margin, y - 8f, right, y - 8f, rulePaint)
                section.rows.forEach { row ->
                    ensure(lineHeight)
                    val paint = if (row.bold) boldPaint else rowPaint
                    val valueWidth = paint.measureText(row.value)
                    val label = fit(row.label, paint, right - margin - valueWidth - 12f)
                    val canvas = page!!.canvas
                    canvas.drawText(label, margin, y + 10f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(row.value, right, y + 10f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += lineHeight
                }
                y += 14f
            }
            page?.let { pdf.finishPage(it) }
            page = null
            FileOutputStream(target).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    // Cuts [text] with an ellipsis so it is at most [maxWidth] points wide.
    private fun fit(text: String, paint: Paint, maxWidth: Float = pageWidth - 2 * margin): String {
        if (paint.measureText(text) <= maxWidth) return text
        val count = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
        return text.take(count).trimEnd() + "…"
    }
}
