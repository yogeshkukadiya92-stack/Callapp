package com.callflow.app.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

object ReportExporter {
    fun shareCsv(context: Context, title: String, headers: List<String>, rows: List<List<String>>) {
        val content = sequenceOf(headers, *rows.toTypedArray()).joinToString("\n") { row -> row.joinToString(",") { csvCell(it) } }
        val file = write(context, safeName(title) + ".csv", content.toByteArray())
        shareFile(context, file, "text/csv", "$title report")
    }

    fun sharePdf(context: Context, title: String, lines: List<String>) {
        val file = File(reportDir(context), safeName(title) + ".pdf")
        val document = PdfDocument()
        try {
            val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
            val bodyPaint = Paint().apply { textSize = 10f }
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            var y = 48f
            page.canvas.drawText(title.take(60), 36f, y, titlePaint); y += 30f
            lines.forEach { source ->
                val chunks = source.ifBlank { " " }.chunked(92)
                chunks.forEach { line ->
                    if (y > 806f) { document.finishPage(page); pageNumber++; page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()); y = 42f }
                    page.canvas.drawText(line, 36f, y, bodyPaint); y += 16f
                }
            }
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        shareFile(context, file, "application/pdf", "$title report")
    }

    fun shareSummary(context: Context, title: String, lines: List<String>) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, (listOf(title) + lines).joinToString("\n")) }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }

    private fun write(context: Context, name: String, bytes: ByteArray) = File(reportDir(context), name).apply { writeBytes(bytes) }
    private fun reportDir(context: Context) = File(context.cacheDir, "shared_reports").apply { mkdirs() }
    private fun shareFile(context: Context, file: File, mime: String, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, subject); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }
    private fun safeName(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "callflow-report" }
    private fun csvCell(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
