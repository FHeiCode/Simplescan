package de.simplescan.app.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Ein Punkt im Bild (Pixel oder 0..1, je nach Zusammenhang). */
data class Pt(val x: Double, val y: Double)

/**
 * Geometrie für die Seitenerkennung (ohne Android/OpenCV, darum per Unit-Test prüfbar):
 * Ecken sortieren, Viereck prüfen, Größe des entzerrten Bildes.
 */
object QuadMath {

    /** Sortiert vier Ecken als oben-links, oben-rechts, unten-rechts, unten-links. */
    fun order(points: List<Pt>): List<Pt> {
        require(points.size == 4)
        val tl = points.minBy { it.x + it.y }
        val br = points.maxBy { it.x + it.y }
        val rest = points.filter { it !== tl && it !== br }.let { if (it.size == 2) it else points - setOf(tl, br) }
        val tr = rest.minBy { it.y - it.x }
        val bl = rest.maxBy { it.y - it.x }
        return listOf(tl, tr, br, bl)
    }

    /** Fläche eines Vielecks (Gaußsche Trapezformel). */
    fun area(quad: List<Pt>): Double {
        var s = 0.0
        for (i in quad.indices) {
            val a = quad[i]
            val b = quad[(i + 1) % quad.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    /** Ist das (sortierte) Viereck konvex? */
    fun isConvex(quad: List<Pt>): Boolean {
        var sign = 0
        for (i in quad.indices) {
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            val c = quad[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /**
     * Taugt das Viereck als Seite? Mindestens [minAreaRatio] des Bildes groß, konvex,
     * und keine Ecke extrem spitz (sonst ist es eher ein Schatten oder eine Tischkante).
     */
    fun plausible(quad: List<Pt>, imageWidth: Int, imageHeight: Int, minAreaRatio: Double = 0.15): Boolean {
        if (quad.size != 4 || !isConvex(quad)) return false
        if (area(quad) < minAreaRatio * imageWidth * imageHeight) return false
        val sides = (0 until 4).map { dist(quad[it], quad[(it + 1) % 4]) }
        return sides.min() > 0.15 * sides.max()
    }

    /** Breite und Höhe des entzerrten Bildes: jeweils die längere der gegenüberliegenden Kanten. */
    fun outputSize(ordered: List<Pt>): Pair<Int, Int> {
        val (tl, tr, br, bl) = ordered
        val w = max(dist(tl, tr), dist(bl, br))
        val h = max(dist(tl, bl), dist(tr, br))
        return w.roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
    }

    /** Typische Handy-Hauptkamera: Brennweite ≈ 0,75 × lange Bildkante (entspricht etwa 26 mm Kleinbild). */
    const val TYPICAL_FOCAL_RATIO = 0.75

    /**
     * Echtes Seitenverhältnis (Breite/Höhe) des fotografierten Rechtecks, aus der perspektivischen Verzerrung
     * der vier Ecken berechnet (Verfahren nach Zhang & He, „Whiteboard scanning“). Die Bildmitte gilt als
     * optische Achse. Grund: Ein schräg fotografiertes Blatt wirkt verkürzt; die reinen Kantenlängen ergäben
     * ein falsches Format (Seite zu breit).
     *
     * [focalPx]: Brennweite in Pixeln, wenn bekannt (aus den Kameradaten). Sonst wird sie aus den Ecken
     * geschätzt – das geht aber nicht, wenn das Handy nur nach vorn gekippt ist (obere und untere Kante
     * bleiben parallel; der häufigste Fall). Dann gilt ein typischer Handywert ([TYPICAL_FOCAL_RATIO]).
     * Null nur bei unbrauchbaren Ecken.
     */
    fun trueAspect(ordered: List<Pt>, imageWidth: Int, imageHeight: Int, focalPx: Double? = null): Double? {
        val (tl, tr, br, bl) = ordered
        val cx = imageWidth / 2.0
        val cy = imageHeight / 2.0
        val m1 = doubleArrayOf(tl.x - cx, tl.y - cy, 1.0)
        val m2 = doubleArrayOf(tr.x - cx, tr.y - cy, 1.0)
        val m3 = doubleArrayOf(bl.x - cx, bl.y - cy, 1.0)
        val m4 = doubleArrayOf(br.x - cx, br.y - cy, 1.0)
        val d2 = dot(cross(m2, m4), m3)
        val d3 = dot(cross(m3, m4), m2)
        if (abs(d2) < 1e-9 || abs(d3) < 1e-9) return null
        val k2 = dot(cross(m1, m4), m3) / d2
        val k3 = dot(cross(m1, m4), m2) / d3
        val n2 = DoubleArray(3) { k2 * m2[it] - m1[it] }
        val n3 = DoubleArray(3) { k3 * m3[it] - m1[it] }
        val longEdge = max(imageWidth, imageHeight).toDouble()
        val f = focalPx?.takeIf { it > 0 } ?: estimateFocal(n2, n3, longEdge) ?: (TYPICAL_FOCAL_RATIO * longEdge)
        val f2 = f * f
        val num = (n2[0] * n2[0] + n2[1] * n2[1]) / f2 + n2[2] * n2[2]
        val den = (n3[0] * n3[0] + n3[1] * n3[1]) / f2 + n3[2] * n3[2]
        if (num <= 0 || den <= 0) return null
        return kotlin.math.sqrt(num / den)
    }

    /** Brennweite aus den Fluchtpunkten schätzen; null, wenn nicht bestimmbar oder unplausibel. */
    private fun estimateFocal(n2: DoubleArray, n3: DoubleArray, longEdge: Double): Double? {
        if (abs(n2[2]) < 1e-3 || abs(n3[2]) < 1e-3) return null
        val f2 = -(n2[0] * n3[0] + n2[1] * n3[1]) / (n2[2] * n3[2])
        if (f2.isNaN() || f2 <= 0) return null
        val f = kotlin.math.sqrt(f2)
        return f.takeIf { it in 0.3 * longEdge..4.0 * longEdge }
    }

    private const val A4 = 0.70710678118654752 // 1 / √2

    /** Liegt das Verhältnis nahe an A4 (hoch oder quer), genau A4 nehmen. */
    fun snapToA4(ratio: Double, tolerance: Double = 0.07): Double = when {
        abs(ratio - A4) / A4 < tolerance -> A4
        abs(ratio - 1 / A4) / (1 / A4) < tolerance -> 1 / A4
        else -> ratio
    }

    /**
     * Größe des entzerrten Bildes mit richtigem Seitenverhältnis: Fläche wie gemessen,
     * Format aus [trueAspect] (gemessenes Format, wenn das nicht geht), nahe A4 → genau A4.
     */
    fun correctedSize(ordered: List<Pt>, imageWidth: Int, imageHeight: Int, focalPx: Double? = null): Pair<Int, Int> {
        val (mw, mh) = outputSize(ordered)
        val ratio = snapToA4(trueAspect(ordered, imageWidth, imageHeight, focalPx) ?: (mw.toDouble() / mh))
        val h = kotlin.math.sqrt(mw.toDouble() * mh / ratio)
        return (h * ratio).roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
    )

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    /** Glättet die Live-Ecken (gegen Zittern): neuer Wert anteilig [alpha]. */
    fun smooth(previous: List<Pt>?, next: List<Pt>, alpha: Double = 0.4): List<Pt> =
        previous?.zip(next) { p, n -> Pt(p.x + (n.x - p.x) * alpha, p.y + (n.y - p.y) * alpha) } ?: next

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}
