package com.example.librarysystem.util

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.librarysystem.data.LabelItem
import java.io.File

/**
 * Lays the picked labels on A4 pages, 4 columns × 6 rows (24 per page), ready
 * for a print shop. Each cell is a QR plus copy number and book title.
 */
object LabelPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val COLS = 4
    private const val ROWS = 6
    private const val MARGIN = 24f
    private const val PER_PAGE = COLS * ROWS

    fun write(context: Context, items: List<LabelItem>): File {
        val doc = PdfDocument()
        val cellW = (PAGE_W - MARGIN * 2) / COLS
        val cellH = (PAGE_H - MARGIN * 2) / ROWS
        val textPaint = Paint().apply {
            textSize = 8f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        var page: PdfDocument.Page? = null
        var pageNo = 0

        items.forEachIndexed { index, item ->
            if (index % PER_PAGE == 0) {
                page?.let { doc.finishPage(it) }
                pageNo += 1
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
            }
            val canvas = page?.canvas ?: return@forEachIndexed
            val slot = index % PER_PAGE
            val col = slot % COLS
            val row = slot / COLS
            val left = MARGIN + col * cellW
            val top = MARGIN + row * cellH

            val qrSize = minOf(cellW, cellH) * 0.62f
            val qrX = left + (cellW - qrSize) / 2
            val qr = QrCodes.bitmap(QrCodes.payload(item.isbn, item.copyNo), 512)
            canvas.drawBitmap(
                qr, null,
                RectF(qrX, top + 4f, qrX + qrSize, top + 4f + qrSize),
                null
            )
            qr.recycle()

            val baseY = top + 4f + qrSize + 12f
            canvas.drawText(item.copyNo, left + cellW / 2, baseY, textPaint)
            canvas.drawText(
                item.bookTitle.take(24),
                left + cellW / 2,
                baseY + 10f,
                textPaint
            )
        }
        page?.let { doc.finishPage(it) }

        val file = File(context.cacheDir, "library-labels.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
