package de.simplescan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Baut aus Seitenfotos (JPEG-Dateien) ein PDF im Cache der App. Für den eigenen Scanner. */
object PdfBuilder {
    /** Fotos unverändert als JPEG ins PDF (klein, siehe JpegPdfWriter). */
    suspend fun build(context: Context, pages: List<File>): File = withContext(Dispatchers.IO) {
        val jpegPages = pages.mapIndexed { i, file ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) throw java.io.IOException("Seite ${i + 1} konnte nicht gelesen werden.")
            JpegPage(file.readBytes(), opts.outWidth, opts.outHeight)
        }
        val dir = File(context.cacheDir, "eigener-scanner").apply { mkdirs() }
        val out = File(dir, "scan-${System.currentTimeMillis()}.pdf")
        out.outputStream().buffered().use { JpegPdfWriter.write(it, jpegPages) }
        out
    }

    /** Speichert ein Foto verkleinert als JPEG (spart Speicher und PDF-Größe). */
    fun savePage(context: Context, bitmap: Bitmap, maxLongEdge: Int = 3000): File {
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
