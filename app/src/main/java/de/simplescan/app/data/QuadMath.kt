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

    /** Glättet die Live-Ecken (gegen Zittern): neuer Wert anteilig [alpha]. */
    fun smooth(previous: List<Pt>?, next: List<Pt>, alpha: Double = 0.4): List<Pt> =
        previous?.zip(next) { p, n -> Pt(p.x + (n.x - p.x) * alpha, p.y + (n.y - p.y) * alpha) } ?: next

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}
