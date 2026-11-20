package de.simplescan.app.data

/** Maße für PDF-Seiten aus Fotos (ohne Android, darum per Unit-Test prüfbar). */
object PdfPages {
    /** A4-Breite in PDF-Punkten (1/72 Zoll). */
    const val A4_WIDTH_PT = 595

    /** Seitengröße in Punkten: A4-Breite, Höhe im Seitenverhältnis des Fotos. */
    fun pageSize(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        require(imageWidth > 0 && imageHeight > 0)
        val h = Math.round(A4_WIDTH_PT.toDouble() * imageHeight / imageWidth).toInt()
        return A4_WIDTH_PT to h.coerceAtLeast(1)
    }

    /** Faktor zum Verkleinern, damit die lange Seite höchstens [maxLongEdge] Pixel hat (nie vergrößern). */
    fun downscale(width: Int, height: Int, maxLongEdge: Int): Double {
        val long = maxOf(width, height)
        return if (long <= maxLongEdge) 1.0 else maxLongEdge.toDouble() / long
    }
}
