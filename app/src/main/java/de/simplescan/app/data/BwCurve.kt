package de.simplescan.app.data

/**
 * Tonkurve für weiches Schwarzweiß (ohne Android, darum per Unit-Test prüfbar):
 * bis Schwelle + [blackOffset] schwarz, ab Schwelle + [whiteOffset] weiß, dazwischen linear.
 * Der Übergang liegt bewusst unter der Schwelle: Die grauen Ränder der Buchstaben werden hell,
 * so bleiben die Striche schlank (sonst wirkt die Schrift aufgeweitet). Die Schwelle wird so
 * begrenzt, dass helles Papier sicher weiß wird.
 */
object BwCurve {
    fun table(threshold: Double, blackOffset: Int = -45, whiteOffset: Int = -5): ByteArray {
        val t = threshold.coerceIn(80.0, 230.0)
        val lo = t + blackOffset
        val hi = t + whiteOffset
        return ByteArray(256) { v ->
            val out = when {
                v <= lo -> 0.0
                v >= hi -> 255.0
                else -> (v - lo) / (hi - lo) * 255.0
            }
            out.toInt().coerceIn(0, 255).toByte()
        }
    }
}
