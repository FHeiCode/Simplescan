package de.simplescan.app.data

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Bildfilter mit OpenCV. Gibt ein neues Bild zurück; das Eingabebild bleibt unverändert. */
object ImageFilters {

    fun apply(src: Bitmap, filter: PageFilter): Bitmap {
        if (filter == PageFilter.ORIGINAL) return src.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat()
        val rgb = Mat()
        val out = Mat()
        try {
            Utils.bitmapToMat(src, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            val even = evenOutLight(rgb)
            when (filter) {
                PageFilter.ENHANCE -> {
                    // Etwas mehr Kontrast, Papier bleibt weiß.
                    even.convertTo(out, -1, 1.12, -18.0)
                    Imgproc.cvtColor(out, out, Imgproc.COLOR_RGB2RGBA)
                }
                PageFilter.GRAY -> {
                    Imgproc.cvtColor(even, out, Imgproc.COLOR_RGB2GRAY)
                    out.convertTo(out, -1, 1.12, -18.0)
                    Imgproc.cvtColor(out, out, Imgproc.COLOR_GRAY2RGBA)
                }
                PageFilter.BW -> {
                    // Weiches Schwarzweiß: statt hartem Schwellwert (Treppenkanten, Krümel) ein schmaler
                    // Übergang um die Otsu-Schwelle – Papier rein weiß, Schrift schwarz, Kanten glatt.
                    val gray = Mat()
                    val tmp = Mat()
                    Imgproc.cvtColor(even, gray, Imgproc.COLOR_RGB2GRAY)
                    Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
                    val t = Imgproc.threshold(gray, tmp, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                    tmp.release()
                    val lut = Mat(1, 256, org.opencv.core.CvType.CV_8UC1)
                    lut.put(0, 0, BwCurve.table(t))
                    Core.LUT(gray, lut, out)
                    lut.release(); gray.release()
                    Imgproc.cvtColor(out, out, Imgproc.COLOR_GRAY2RGBA)
                }
                PageFilter.ORIGINAL -> Unit
            }
            even.release()
            val result = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, result)
            return result
        } finally {
            rgba.release(); rgb.release(); out.release()
        }
    }

    /**
     * Gleicht ungleichmäßiges Licht aus und macht das Papier weiß: Hintergrund schätzen
     * (Schrift wegfiltern, stark weichzeichnen, auf kleinem Bild – schneller) und das Bild dadurch teilen.
     */
    private fun evenOutLight(rgb: Mat): Mat {
        val small = Mat()
        val bg = Mat()
        val result = Mat()
        try {
            Imgproc.resize(rgb, small, Size(), 0.25, 0.25, Imgproc.INTER_AREA)
            Imgproc.dilate(small, small, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0)))
            Imgproc.medianBlur(small, small, 15)
            Imgproc.resize(small, bg, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Core.divide(rgb, bg, result, 255.0)
            return result
        } finally {
            small.release(); bg.release()
        }
    }
}
