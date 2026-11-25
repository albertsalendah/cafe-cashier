package com.albertsalendah.cafecashier.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.albertsalendah.cafecashier.domain.PdfDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val MIME_CSV = "text/csv"
const val MIME_PDF = "application/pdf"

// Writes the report files into the app's cache folder (cacheDir/exports) and hands them to the share
// sheet through the FileProvider declared in the manifest. Old exports are removed before each new
// one, so the cache never grows.
class ReportExporter(private val context: Context) {

    private val dir: File get() = File(context.cacheDir, "exports")

    private fun freshDir(): File {
        val d = dir
        d.listFiles()?.forEach { it.delete() }
        d.mkdirs()
        return d
    }

    // [text] already starts with the UTF-8 mark (see CsvExport).
    suspend fun writeCsv(fileName: String, text: String): File = withContext(Dispatchers.IO) {
        File(freshDir(), fileName).also { it.writeText(text, Charsets.UTF_8) }
    }

    suspend fun writePdf(fileName: String, doc: PdfDoc): File = withContext(Dispatchers.IO) {
        File(freshDir(), fileName).also { ReportPdfWriter().write(doc, it) }
    }

    // Opens the system share sheet for [file]. Receivers get read access to this one file only.
    fun share(file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
