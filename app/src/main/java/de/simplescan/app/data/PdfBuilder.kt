package de.simplescan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Baut aus Seitenfotos (JPEG-Dateien) ein PDF im Cache der App. Für den eigenen Scanner. */
object PdfBuilder {
    suspend fun build(context: Context, pages: List<File>): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, file ->
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw java.io.IOException("Seite ${i + 1} konnte nicht gelesen werden.")
                val (w, h) = PdfPages.pageSize(bmp.width, bmp.height)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bmp.recycle()
            }
            val dir = File(context.cacheDir, "eigener-scanner").apply { mkdirs() }
            val out = File(dir, "scan-${System.currentTimeMillis()}.pdf")
            out.outputStream().use { doc.writeTo(it) }
            out
        } finally {
            doc.close()
        }
    }

    /** Speichert ein Foto verkleinert als JPEG (spart Speicher und PDF-Größe). */
    fun savePage(context: Context, bitmap: Bitmap, maxLongEdge: Int = 2000): File {
        val f = PdfPages.downscale(bitmap.width, bitmap.height, maxLongEdge)
        val scaled = if (f < 1.0) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt(), (bitmap.height * f).toInt(), true)
        } else bitmap
        val dir = File(context.cacheDir, "eigener-scanner").apply { mkdirs() }
        val file = File(dir, "seite-${System.nanoTime()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (scaled !== bitmap) scaled.recycle()
        return file
    }
}
