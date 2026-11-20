package de.simplescan.app.data

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Seitenerkennung mit OpenCV: sucht das größte plausible Viereck im Bild.
 * Zwei Durchgänge: Kanten (Canny) und Helligkeit (Otsu) – Letzteres hilft bei hellem Blatt auf dunklem Grund.
 */
object PageDetector {

    /** Erkennung in einem Graustufenbild. Ecken (sortiert) in Pixeln dieses Bildes oder null. */
    fun detect(gray: Mat): List<Pt>? {
        val blur = Mat()
        val work = Mat()
        try {
            Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
            // 1) Kanten
            Imgproc.Canny(blur, work, 40.0, 120.0)
            Imgproc.morphologyEx(
                work, work, Imgproc.MORPH_CLOSE,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)),
            )
            bestQuad(work, gray.cols(), gray.rows())?.let { return it }
            // 2) Helligkeit
            Imgproc.threshold(blur, work, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            return bestQuad(work, gray.cols(), gray.rows())
        } finally {
            blur.release(); work.release()
        }
    }

    private fun bestQuad(binary: Mat, w: Int, h: Int): List<Pt>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        try {
            val candidates = contours.sortedByDescending { Imgproc.contourArea(it) }.take(8)
            for (c in candidates) {
                val c2f = MatOfPoint2f(*c.toArray())
                val approx = MatOfPoint2f()
                val peri = Imgproc.arcLength(c2f, true)
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                val pts = approx.toArray()
                c2f.release(); approx.release()
                if (pts.size == 4) {
                    val quad = QuadMath.order(pts.map { Pt(it.x, it.y) })
                    if (QuadMath.plausible(quad, w, h)) return quad
                }
            }
            return null
        } finally {
            contours.forEach { it.release() }
        }
    }

    /** Erkennung in einem Foto: rechnet verkleinert und gibt Ecken in Pixeln des Fotos zurück. */
    fun detect(photo: Bitmap, workLongEdge: Int = 900): List<Pt>? {
        val scale = workLongEdge.toDouble() / max(photo.width, photo.height)
        val small = if (scale < 1) {
            Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true)
        } else photo
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val f = if (scale < 1) 1 / scale else 1.0
            return detect(gray)?.map { Pt(it.x * f, it.y * f) }
        } finally {
            rgba.release(); gray.release()
            if (small !== photo) small.recycle()
        }
    }

    /**
     * Schneidet das Viereck aus und zieht es gerade (Perspektive entzerren), im echten Seitenverhältnis
     * (siehe QuadMath.correctedSize). Ringsum fällt ein schmaler Rand weg, damit kein Streifen vom Untergrund bleibt.
     */
    fun warp(photo: Bitmap, ordered: List<Pt>, marginRatio: Double = 0.008): Bitmap {
        val (w, h) = QuadMath.correctedSize(ordered, photo.width, photo.height)
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
