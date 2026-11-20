package de.simplescan.app

import de.simplescan.app.data.JpegPage
import de.simplescan.app.data.JpegPdfWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegPdfWriterTest {
    private val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun xrefOffsetsPointAtObjects() {
        val bytes = JpegPdfWriter.toBytes(listOf(JpegPage(fakeJpeg, 3000, 2250), JpegPage(fakeJpeg, 2250, 3000)))
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Count 2"))
        assertTrue(text.contains("/Filter /DCTDecode"))
        // startxref zeigt auf „xref“
        val start = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertEquals("xref", text.substring(start, start + 4))
        // jeder Eintrag zeigt auf „N 0 obj“
        val entries = text.substring(start).lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertEquals(8, entries.size) // Katalog, Seitenbaum, 2 × (Seite, Inhalt, Bild)
        entries.forEachIndexed { i, line ->
            val off = line.substring(0, 10).toInt()
            assertTrue("Objekt ${i + 1}", text.startsWith("${i + 1} 0 obj", off))
        }
    }

    @Test
    fun pageSizeFollowsImage() {
        val text = String(JpegPdfWriter.toBytes(listOf(JpegPage(fakeJpeg, 3000, 4000))), Charsets.ISO_8859_1)
        assertTrue(text.contains("/MediaBox [0 0 595 793]"))
    }
}
