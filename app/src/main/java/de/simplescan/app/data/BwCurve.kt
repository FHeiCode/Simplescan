package de.simplescan.app.data

/**
 * Tonkurve für weiches Schwarzweiß (ohne Android, darum per Unit-Test prüfbar):
 * bis [below] unter der Schwelle schwarz, ab [above] darüber weiß, dazwischen linear.
 * Die Schwelle wird so begrenzt, dass helles Papier sicher weiß wird.
 */
object BwCurve {
    fun table(threshold: Double, below: Int = 30, above: Int = 12): ByteArray {
        val t = threshold.coerceIn(80.0, 230.0)
        val lo = t - below
        val hi = t + above
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
