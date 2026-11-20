package de.simplescan.app.data

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Eine Seite als fertiges JPEG mit seinen Pixelmaßen. */
class JpegPage(val jpeg: ByteArray, val width: Int, val height: Int)

/**
 * Schreibt ein PDF, in dem jede Seite ein JPEG-Foto ist – unverändert eingebettet (DCTDecode).
 * Grund: Androids PdfDocument speichert Fotos praktisch unkomprimiert (3 Seiten ≈ 10 MB).
 * So bleibt das PDF etwa so groß wie die JPEGs selbst. Ohne Android, darum per Unit-Test prüfbar.
 */
object JpegPdfWriter {

    fun write(out: OutputStream, pages: List<JpegPage>) {
        require(pages.isNotEmpty()) { "Keine Seiten" }
        val buf = CountingStream(out)
        val offsets = mutableListOf<Long>() // Index = Objektnummer - 1

        fun obj(num: Int, body: () -> Unit) {
            while (offsets.size < num) offsets += 0L
            offsets[num - 1] = buf.count
            buf.ascii("$num 0 obj\n")
            body()
            buf.ascii("\nendobj\n")
        }

        buf.ascii("%PDF-1.4\n")
        buf.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        // Objekte: 1 Katalog, 2 Seitenbaum, dann je Seite: Seite, Inhalt, Bild
        val pageObj = { i: Int -> 3 + i * 3 }
        obj(1) { buf.ascii("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) {
            val kids = pages.indices.joinToString(" ") { "${pageObj(it)} 0 R" }
            buf.ascii("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
        }
        pages.forEachIndexed { i, p ->
            val (w, h) = PdfPages.pageSize(p.width, p.height)
            val pNum = pageObj(i)
            obj(pNum) {
                buf.ascii(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
                        "/Resources << /XObject << /Im0 ${pNum + 2} 0 R >> >> /Contents ${pNum + 1} 0 R >>"
                )
            }
            val content = "q $w 0 0 $h 0 0 cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
            obj(pNum + 1) {
                buf.ascii("<< /Length ${content.size} >>\nstream\n")
                buf.write(content)
                buf.ascii("\nendstream")
            }
            obj(pNum + 2) {
                buf.ascii(
                    "<< /Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n"
                )
                buf.write(p.jpeg)
                buf.ascii("\nendstream")
            }
        }

        val xref = buf.count
        buf.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { buf.ascii(String.format("%010d 00000 n \n", it)) }
        buf.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        buf.flush()
    }

    fun toBytes(pages: List<JpegPage>): ByteArray = ByteArrayOutputStream().also { write(it, pages) }.toByteArray()

    private class CountingStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
        override fun flush() = out.flush()
        fun ascii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
    }
}
