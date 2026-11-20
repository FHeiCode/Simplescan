package de.simplescan.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.simplescan.app.data.EditGeometry
import de.simplescan.app.data.ImageFilters
import de.simplescan.app.data.PageFilter
import de.simplescan.app.data.PageDetector
import de.simplescan.app.data.PdfBuilder
import de.simplescan.app.data.Pt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Eine gescannte Seite im eigenen Scanner: Originalfoto (für späteres Nachbessern der Ecken),
 * gefundene Ecken (in Foto-Pixeln, null = ganzes Foto), Drehung und die fertige Seite fürs PDF.
 */
data class ScanPage(
    val id: Long,
    val photo: File,
    val photoWidth: Int,
    val photoHeight: Int,
    val quad: List<Pt>?,
    val rotation: Int,
    val filter: PageFilter,
    val rendered: File,
    val thumb: ImageBitmap,
)

/** Alle Seiten eines Scan-Vorgangs. Lebt nur, solange der eigene Scanner offen ist. */
class ScanSession {
    val pages = mutableStateListOf<ScanPage>()

    /** Filter für neue Seiten (und „für alle“ in der Übersicht). */
    var filter by mutableStateOf(PageFilter.ENHANCE)

    fun replace(page: ScanPage) {
        val i = pages.indexOfFirst { it.id == page.id }
        if (i >= 0) {
            val old = pages[i]
            pages[i] = page
            if (old.rendered != page.rendered) old.rendered.delete()
        }
    }

    fun remove(id: Long) {
        val i = pages.indexOfFirst { it.id == id }
        if (i >= 0) pages.removeAt(i).also { it.photo.delete(); it.rendered.delete() }
    }

    fun move(id: Long, delta: Int) {
        val i = pages.indexOfFirst { it.id == id }
        val moved = EditGeometry.move(pages.toList(), i, delta)
        pages.clear(); pages.addAll(moved)
    }

    /** Alle Dateien wegräumen (Abbrechen). */
    fun discard() {
        pages.forEach { it.photo.delete(); it.rendered.delete() }
        pages.clear()
    }

    /** Für das PDF: fertige Seiten; die Originalfotos werden nicht mehr gebraucht. */
    fun finish(): List<File> {
        val files = pages.map { it.rendered }
        pages.forEach { it.photo.delete() }
        pages.clear()
        return files
    }
}

object PageRenderer {
    /** Originalfoto als JPEG ablegen (hohe Qualität, weil daraus später neu zugeschnitten wird). */
    fun savePhoto(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "eigener-scanner").apply { mkdirs() }
        val f = File(dir, "foto-${System.nanoTime()}.jpg")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return f
    }

    /** Fertige Seite aus Foto, Ecken und Drehung erzeugen (zuschneiden, entzerren, drehen). */
    suspend fun render(
        context: Context, id: Long, photo: File, quad: List<Pt>?, rotation: Int, filter: PageFilter,
    ): ScanPage = withContext(Dispatchers.Default) {
        val src = BitmapFactory.decodeFile(photo.absolutePath) ?: throw java.io.IOException("Foto nicht lesbar")
        val pw = src.width
        val ph = src.height
        var page = if (quad != null) PageDetector.warp(src, quad).also { src.recycle() } else src
        if (filter != PageFilter.ORIGINAL) {
            page = ImageFilters.apply(page, filter).also { page.recycle() }
        }
        if (rotation % 360 != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            page = Bitmap.createBitmap(page, 0, 0, page.width, page.height, m, true).also { if (it !== page) page.recycle() }
        }
        // Schwarzweiß mit weniger Kompression, sonst zeigen sich Artefakte an den Buchstabenkanten.
        val rendered = PdfBuilder.savePage(context, page, quality = if (filter == PageFilter.BW) 95 else 85)
        val thumb = Bitmap.createScaledBitmap(page, 240, (240f * page.height / page.width).toInt().coerceAtLeast(1), true)
        page.recycle()
        ScanPage(id, photo, pw, ph, quad, rotation, filter, rendered, thumb.asImageBitmap())
    }
}

/** Foto aus der Galerie laden: richtig gedreht (EXIF), höchstens [maxLongEdge] Pixel, als ARGB_8888. */
fun loadGalleryImage(context: Context, uri: android.net.Uri, maxLongEdge: Int = 3000): Bitmap {
    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
    val bmp = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        val f = de.simplescan.app.data.PdfPages.downscale(info.size.width, info.size.height, maxLongEdge)
        if (f < 1.0) decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
    }
    return if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
}
