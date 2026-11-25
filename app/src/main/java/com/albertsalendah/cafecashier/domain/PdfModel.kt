package com.albertsalendah.cafecashier.domain

// What the PDF report prints, already worded and formatted. The writer only draws it, so the page
// layout can change without touching the numbers.
data class PdfRow(val label: String, val value: String, val bold: Boolean = false)

data class PdfSection(val heading: String, val rows: List<PdfRow>)

data class PdfDoc(val title: String, val subtitle: String, val sections: List<PdfSection>)
