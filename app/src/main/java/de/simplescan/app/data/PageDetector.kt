package de.simplescan.app.data

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Seitenerkennung mit OpenCV.
 *
 * Mehrere Karten aus demselben Bild (Kanten im Grau mit Schwellen aus dem Bild, Kanten in der Farbsättigung,
 * schwache Kanten nach örtlicher Kontrastanhebung für weißes Blatt auf hellem Tisch, „Papierfläche“ hell und
 * ungesättigt, Otsu-Helligkeit). Aus allen Konturen werden Vierecke gebildet, die Ecken über Geraden durch die
 * Seiten verfeinert (robust gegen Eselsohren, runde Ecken, Finger) und nach Kantentreue bewertet. Liegt das beste
 * Viereck in einem größeren, das ebenfalls passt (Tabelle/Kasten im Blatt), gewinnt das größere.
 *
 * Dazu: keine Seite am Bildrand, jede Seite einzeln auf einer Kante, gerade Seiten, Faltfelder zusammensetzen.
 *
 * Gemessen an künstlichen Testfotos mit Fingern, Eselsohren, Faltungen und Lichtflecken (tools/erkennung-test,
 * 280 Fotos): Foto 230 richtig (bis 2.2: 71), live in Graustufen 208 (bis 2.2: 67).
 */
object PageDetector {

    /** Live-Erkennung in einem kleinen Graustufenbild (Y-Ebene der Kamera). Ecken in Pixeln dieses Bildes. */
    fun detect(gray: Mat): List<Pt>? = detectMaps(gray, null)

    /** Erkennung in einem Foto: rechnet verkleinert (Farbe hilft) und gibt Ecken in Pixeln des Fotos zurück. */
    fun detect(photo: Bitmap, workLongEdge: Int = 800): List<Pt>? {
        val scale = workLongEdge.toDouble() / max(photo.width, photo.height)
        val small = if (scale < 1) {
            Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true)
        } else photo
        val rgba = Mat(); val rgb = Mat(); val gray = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            val f = if (scale < 1) 1 / scale else 1.0
            return detectMaps(gray, rgb)?.map { Pt(it.x * f, it.y * f) }
        } finally {
            rgba.release(); rgb.release(); gray.release()
            if (small !== photo) small.recycle()
        }
    }

    private fun detectMaps(gray: Mat, rgb: Mat?): List<Pt>? {
        val w = gray.cols(); val h = gray.rows()
        val toRelease = ArrayList<Mat>()
        fun mat() = Mat().also { toRelease += it }
        try {
            val blur = mat(); Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
            val med = median(blur)
            val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)).also { toRelease += it }
            val k15 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0)).also { toRelease += it }
            val maps = ArrayList<Mat>()

            // 1) Kanten im Grau, Schwellen aus dem Bild
            val e1 = mat(); Imgproc.Canny(blur, e1, max(10.0, 0.4 * med), max(30.0, 1.0 * med))
            // 2) Kanten in der Sättigung (weißes Papier vor farbigem Grund) – nur mit Farbbild
            var e2: Mat? = null
            var hsv: Mat? = null
            if (rgb != null) {
                hsv = mat(); Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
                val ch = ArrayList<Mat>(); Core.split(hsv, ch); toRelease += ch
                val sBlur = mat(); Imgproc.GaussianBlur(ch[1], sBlur, Size(5.0, 5.0), 0.0)
                e2 = mat(); Imgproc.Canny(sBlur, e2, 15.0, 45.0)
                // 4) Fläche „Papier“: hell und ungesättigt, Schrift-Löcher schließen
                val paper = mat(); Core.subtract(ch[2], ch[1], paper)
                Imgproc.GaussianBlur(paper, paper, Size(7.0, 7.0), 0.0)
                val pm = mat(); Imgproc.threshold(paper, pm, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                Imgproc.morphologyEx(pm, pm, Imgproc.MORPH_CLOSE, k15)
                Imgproc.morphologyEx(
                    pm, pm, Imgproc.MORPH_OPEN,
                    Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)).also { toRelease += it },
                )
                maps += pm
            }
            // 3) schwache Kanten: örtlich Kontrast anheben, niedrige Schwellen
            val soft = mat(); Imgproc.GaussianBlur(gray, soft, Size(7.0, 7.0), 0.0)
            val cl = mat(); Imgproc.createCLAHE(3.0, Size(8.0, 8.0)).apply(soft, cl)
            val e3 = mat(); Imgproc.Canny(cl, e3, 12.0, 36.0)

            // Kantenbild für die Bewertung
            val all = mat(); Core.bitwise_or(e1, e3, all)
            val e12 = mat()
            if (e2 != null) { Core.bitwise_or(all, e2, all); Core.bitwise_or(e1, e2, e12) } else e1.copyTo(e12)
            val edges = mat(); Imgproc.dilate(all, edges, Mat.ones(3, 3, CvType.CV_8UC1).also { toRelease += it })
            val edgeBytes = ByteArray(w * h).also { edges.get(0, 0, it) }

            val m1 = mat(); Imgproc.morphologyEx(e12, m1, Imgproc.MORPH_CLOSE, k5); maps += m1
            val m3 = mat(); Imgproc.morphologyEx(e3, m3, Imgproc.MORPH_CLOSE, k5); maps += m3
            // 5) Otsu im Grau
            val om = mat(); Imgproc.threshold(blur, om, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(om, om, Imgproc.MORPH_CLOSE, k15); maps += om

            // Kandidaten: Viereck, mittlere Kantentreue, schwächste Seite
            val found = ArrayList<Triple<List<Pt>, Double, Double>>()
            for (m in maps) {
                val contours = ArrayList<MatOfPoint>()
                val hier = Mat()
                Imgproc.findContours(m, contours, hier, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                hier.release()
                try {
                    for (c in contours.sortedByDescending { Imgproc.contourArea(it) }.take(12)) {
                        val (q0, hull) = quadFromContour(c) ?: continue
                        if (!QuadMath.plausibleCandidate(q0, w, h)) continue
                        val (q, bend) = refine(q0, hull)
                        // Seiten müssen gerade sein (Papierkante gerade, Rand eines Lichtflecks rund).
                        if (bend > MAX_BEND || !QuadMath.plausibleCandidate(q, w, h)) continue
                        val sides = sideSupport(q, edgeBytes, w, h)
                        found += Triple(q, sides.average(), sides.min())
                    }
                } finally {
                    contours.forEach { it.release() }
                }
            }
            // Jede Seite muss auf einer echten Kante liegen (gegen erfundene Seiten quer über den Tisch).
            val good = found.filter { it.second >= 0.45 && it.third >= MIN_SIDE }
            if (good.isEmpty()) return null
            val imgArea = w.toDouble() * h
            var best = good.maxBy { it.second * kotlin.math.sqrt(QuadMath.area(it.first) / imgArea) }.first
            // Faltfeld/Tabelle im Blatt? Dann das umgebende Blatt nehmen – es darf eine schwächere Seite haben,
            // weil das kräftige innere Viereck es bestätigt.
            val outer = found
                .filter { it.second >= 0.45 && it.third >= MIN_SIDE_OUTER }
                .map { it.first }
                .filter { QuadMath.area(it) > 1.25 * QuadMath.area(best) && QuadMath.inside(best, it) }
            if (outer.isNotEmpty()) best = outer.maxBy { QuadMath.area(it) }
            // Gefalteter Brief: Felder, die sich eine Knicklinie teilen, zum ganzen Blatt zusammensetzen.
            val tol = 0.03 * kotlin.math.hypot(w.toDouble(), h.toDouble())
            repeat(3) {
                var grown: List<Pt>? = null
                for ((q, _, _) in found) {
                    val u = QuadMath.mergeAlongSharedSide(best, q, tol) ?: continue
                    if (!QuadMath.plausibleCandidate(u, w, h) || QuadMath.area(u) <= 1.1 * QuadMath.area(best)) continue
                    val sides = sideSupport(u, edgeBytes, w, h)
                    if (sides.average() >= 0.45 && sides.min() >= MIN_SIDE_OUTER &&
                        (grown == null || QuadMath.area(u) > QuadMath.area(grown!!))
                    ) grown = u
                }
                best = grown ?: return best
            }
            return best
        } finally {
            toRelease.forEach { it.release() }
        }
    }

    /** Viereck aus einer Kontur: konvexe Hülle, dann vereinfachen, bis vier Ecken übrig sind. */
    private fun quadFromContour(c: MatOfPoint): Pair<List<Pt>, List<Pt>>? {
        val idx = MatOfInt()
        Imgproc.convexHull(c, idx)
        val pts = c.toArray()
        val hull = idx.toArray().map { pts[it] }
        idx.release()
        if (hull.size < 4) return null
        val hull2f = MatOfPoint2f(*hull.toTypedArray())
        try {
            val peri = Imgproc.arcLength(hull2f, true)
            for (eps in doubleArrayOf(0.01, 0.015, 0.02, 0.03, 0.04, 0.05, 0.065, 0.08)) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(hull2f, approx, eps * peri, true)
                val a = approx.toArray(); approx.release()
                if (a.size == 4) return QuadMath.order(a.map { Pt(it.x, it.y) }) to hull.map { Pt(it.x, it.y) }
                if (a.size < 4) return null
            }
            return null
        } finally {
            hull2f.release()
        }
    }

    private const val MIN_SIDE = 0.3        // jede Seite so weit auf einer Kante
    private const val MIN_SIDE_OUTER = 0.18 // für das Blatt um ein kräftiges inneres Viereck
    private const val MAX_BEND = 0.012      // Seiten gerade: Abweichung höchstens 1,2 % der Seitenlänge

    /**
     * Geraden durch die Konturpunkte jeder Seite (ohne die Eckbereiche), Ecken = Schnittpunkte.
     * Gibt die Ecken und die größte Krümmung einer Seite (mittlere Abweichung / Seitenlänge) zurück.
     */
    private fun refine(q: List<Pt>, hull: List<Pt>): Pair<List<Pt>, Double> {
        if (hull.size < 8) return q to 0.0
        val dense = ArrayList<Pt>()
        for (i in hull.indices) {
            val a = hull[i]; val b = hull[(i + 1) % hull.size]
            val n = max(2, (kotlin.math.hypot(b.x - a.x, b.y - a.y) / 3).toInt())
            for (k in 0 until n) { val t = k.toDouble() / n; dense += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }
        }
        val lines = ArrayList<Pair<Pt, Pt>>()
        var bend = 0.0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
            val sel = dense.filter { p ->
                val rx = p.x - a.x; val ry = p.y - a.y
                val t = rx * ux + ry * uy
                val off = kotlin.math.abs(-uy * rx + ux * ry)
                t > 0.12 * len && t < 0.88 * len && off < 0.03 * len + 3
            }
            if (sel.size < 5) return q to 0.0
            val m = MatOfPoint2f(*sel.map { Point(it.x, it.y) }.toTypedArray())
            val line = Mat()
            Imgproc.fitLine(m, line, Imgproc.DIST_HUBER, 0.0, 0.01, 0.01)
            val v = FloatArray(4); line.get(0, 0, v)
            m.release(); line.release()
            lines += Pt(v[2].toDouble(), v[3].toDouble()) to Pt(v[0].toDouble(), v[1].toDouble())
            val nx = -v[1].toDouble(); val ny = v[0].toDouble()
            val rms = kotlin.math.sqrt(sel.sumOf { val d = (it.x - v[2]) * nx + (it.y - v[3]) * ny; d * d } / sel.size)
            bend = max(bend, rms / len)
        }
        val out = (0 until 4).map { i ->
            val (p1, d1) = lines[(i + 3) % 4]; val (p2, d2) = lines[i]
            QuadMath.intersect(p1, d1, p2, d2) ?: return q to bend
        }
        val r = QuadMath.order(out)
        // Die Verfeinerung darf nicht weit wegspringen.
        return (if (QuadMath.maxCornerShift(r, q) > 0.12 * kotlin.math.sqrt(QuadMath.area(q))) q else r) to bend
    }

    /** Je Seite: Anteil der Prüfpunkte, die auf einer Kante liegen. */
    private fun sideSupport(q: List<Pt>, edges: ByteArray, w: Int, h: Int): List<Double> =
        QuadMath.sidePoints(q).map { pts ->
            pts.count { p ->
                val x = p.x.toInt(); val y = p.y.toInt()
                x in 0 until w && y in 0 until h && edges[y * w + x].toInt() != 0
            }.toDouble() / pts.size
        }

    /** Median der Helligkeit (für die Kanten-Schwellen). */
    private fun median(gray: Mat): Double {
        val bytes = ByteArray((gray.total() * gray.channels()).toInt()).also { gray.get(0, 0, it) }
        val hist = IntArray(256)
        for (b in bytes) hist[b.toInt() and 0xFF]++
        var acc = 0
        val half = bytes.size / 2
        for (v in 0 until 256) { acc += hist[v]; if (acc >= half) return v.toDouble() }
        return 128.0
    }

    /**
     * Schneidet das Viereck aus und zieht es gerade (Perspektive entzerren), im echten Seitenverhältnis
     * (siehe QuadMath.correctedSize). Ringsum fällt ein schmaler Rand weg, damit kein Streifen vom Untergrund bleibt.
     */
    fun warp(photo: Bitmap, ordered: List<Pt>, focalRatio: Double? = null, marginRatio: Double = 0.008): Bitmap {
        // focalRatio: Brennweite je Pixel der langen Bildkante (aus den Kameradaten), für das echte Seitenverhältnis.
        val focalPx = focalRatio?.let { it * max(photo.width, photo.height) }
        val (w, h) = QuadMath.correctedSize(ordered, photo.width, photo.height, focalPx)
        val src = MatOfPoint2f(*ordered.map { Point(it.x, it.y) }.toTypedArray())
        val dst = MatOfPoint2f(
            Point(0.0, 0.0), Point(w - 1.0, 0.0), Point(w - 1.0, h - 1.0), Point(0.0, h - 1.0),
        )
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val input = Mat()
        val output = Mat(h, w, CvType.CV_8UC4)
        try {
            Utils.bitmapToMat(photo, input)
            Imgproc.warpPerspective(input, output, m, Size(w.toDouble(), h.toDouble()), Imgproc.INTER_LINEAR)
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(output, full)
            val dx = (w * marginRatio).toInt()
            val dy = (h * marginRatio).toInt()
            if (dx == 0 && dy == 0) return full
            return Bitmap.createBitmap(full, dx, dy, w - 2 * dx, h - 2 * dy).also { if (it !== full) full.recycle() }
        } finally {
            src.release(); dst.release(); m.release(); input.release(); output.release()
        }
    }

    /** Graustufenbild aus der Y-Ebene eines Kamera-Vorschaubilds, gedreht und verkleinert. */
    fun grayFromY(
        y: java.nio.ByteBuffer, width: Int, height: Int, rowStride: Int, rotationDegrees: Int, maxLongEdge: Int = 480,
    ): Mat {
        val bytes = ByteArray(width * height)
        y.rewind()
        if (rowStride == width) {
            y.get(bytes, 0, width * height)
        } else {
            val row = ByteArray(rowStride)
            for (r in 0 until height) {
                y.position(r * rowStride)
                y.get(row, 0, minOf(rowStride, y.remaining()))
                System.arraycopy(row, 0, bytes, r * width, width)
            }
        }
        val full = Mat(height, width, CvType.CV_8UC1)
        full.put(0, 0, bytes)
        val scale = maxLongEdge.toDouble() / max(width, height)
        val small = Mat()
        if (scale < 1) Imgproc.resize(full, small, Size(width * scale, height * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        else full.copyTo(small)
        full.release()
        when ((rotationDegrees % 360 + 360) % 360) {
            90 -> Core.rotate(small, small, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(small, small, Core.ROTATE_180)
            270 -> Core.rotate(small, small, Core.ROTATE_90_COUNTERCLOCKWISE)
        }
        return small
    }
}
