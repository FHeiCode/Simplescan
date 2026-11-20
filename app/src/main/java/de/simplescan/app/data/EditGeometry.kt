package de.simplescan.app.data

import kotlin.math.hypot

/** Hilfen für Seitenübersicht und Ecken-Editor (ohne Android, darum per Unit-Test prüfbar). */
object EditGeometry {

    /** Verschiebt das Element an [from] um [delta] Plätze (begrenzt auf die Liste). */
    fun <T> move(list: List<T>, from: Int, delta: Int): List<T> {
        if (from !in list.indices) return list
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Drehung um 90° im Uhrzeigersinn, Ergebnis 0, 90, 180 oder 270. */
    fun rotateRight(degrees: Int): Int = ((degrees + 90) % 360 + 360) % 360

    /** Bildfläche bei „einpassen“ in einen Kasten: Versatz x, y und Maßstab. */
    data class Fit(val offsetX: Double, val offsetY: Double, val scale: Double) {
        fun toView(p: Pt) = Pt(offsetX + p.x * scale, offsetY + p.y * scale)
        fun toImage(x: Double, y: Double) = Pt((x - offsetX) / scale, (y - offsetY) / scale)
    }

    fun fit(imageWidth: Int, imageHeight: Int, boxWidth: Double, boxHeight: Double): Fit {
        val scale = minOf(boxWidth / imageWidth, boxHeight / imageHeight)
        return Fit((boxWidth - imageWidth * scale) / 2, (boxHeight - imageHeight * scale) / 2, scale)
    }

    /** Index der Ecke, die einem Fingertipp am nächsten ist (innerhalb [radius]), sonst null. */
    fun hitCorner(cornersInView: List<Pt>, x: Double, y: Double, radius: Double): Int? =
        cornersInView.withIndex()
            .map { (i, p) -> i to hypot(p.x - x, p.y - y) }
            .filter { it.second <= radius }
            .minByOrNull { it.second }?.first

    /** Punkt auf die Bildfläche begrenzen. */
    fun clamp(p: Pt, width: Int, height: Int) = Pt(p.x.coerceIn(0.0, width - 1.0), p.y.coerceIn(0.0, height - 1.0))

    /** Ecken für „ganzes Foto“. */
    fun fullFrame(width: Int, height: Int) = listOf(
        Pt(0.0, 0.0), Pt(width - 1.0, 0.0), Pt(width - 1.0, height - 1.0), Pt(0.0, height - 1.0),
    )
}
