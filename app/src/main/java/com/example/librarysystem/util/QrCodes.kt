package com.example.librarysystem.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Library QR labels.
 *
 * Payload is `ISBN|COPY_NO` (for example `9780553380163|C1`) so a scan can
 * identify both the title and the exact physical copy. `|` is used instead of
 * `-` because ISBNs are sometimes typed with hyphens.
 */
object QrCodes {

    private const val SEPARATOR = '|'

    fun payload(isbn: String?, copyNo: String): String =
        "${isbn?.trim().orEmpty()}${SEPARATOR}${copyNo.trim()}"

    /** Splits a scanned payload; returns null when it is not a library label. */
    fun parse(raw: String): Pair<String, String>? {
        val text = raw.trim()
        val idx = text.indexOf(SEPARATOR)
        if (idx <= 0 || idx == text.lastIndex) return null
        val isbn = text.substring(0, idx).trim()
        val copyNo = text.substring(idx + 1).trim()
        if (isbn.isEmpty() || copyNo.isEmpty()) return null
        return isbn to copyNo
    }

    /** ISBN part of a scanned label, or the raw text when it is not one. */
    fun isbnOf(raw: String): String = parse(raw)?.first ?: raw.trim()

    fun bitmap(content: String, size: Int = 768): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (matrix[x, y]) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
