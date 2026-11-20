package de.simplescan.app.data

import kotlin.math.hypot

/**
 * Beruhigt den Live-Rahmen (Ecken in 0..1). Ohne Android, darum per Unit-Test prüfbar.
 * - kleine Abweichungen (unter [deadband]) werden ignoriert,
 * - normale Bewegungen fließen nur anteilig ([alpha]) ein,
 * - ein ganz anderes Viereck (Sprung über [jump]) wird erst nach [confirmFrames] gleichartigen Bildern übernommen,
 * - der Rahmen verschwindet erst nach [hideAfter] Bildern ohne Erkennung,
 * - zum ersten Anzeigen braucht es [showAfter] Treffer hintereinander.
 */
class QuadStabilizer(
    private val alpha: Double = 0.25,
    private val deadband: Double = 0.006,
    private val jump: Double = 0.08,
    private val confirmFrames: Int = 3,
    private val hideAfter: Int = 8,
    private val showAfter: Int = 2,
) {
    var current: List<Pt>? = null
        private set
    private var missing = 0
    private var hits = 0
    private var candidate: List<Pt>? = null
    private var candidateCount = 0

    fun update(next: List<Pt>?): List<Pt>? {
        if (next == null) {
            hits = 0
            candidate = null; candidateCount = 0
            if (++missing >= hideAfter) current = null
            return current
        }
        missing = 0
        val cur = current
        if (cur == null) {
            if (++hits >= showAfter) current = next
            return current
        }
        val d = meanDistance(cur, next)
        when {
            d < deadband -> Unit
            d < jump -> { current = QuadMath.smooth(cur, next, alpha); candidate = null; candidateCount = 0 }
            else -> {
                // Sprung: nur übernehmen, wenn das neue Viereck mehrere Bilder lang stabil bleibt.
                val c = candidate
                if (c != null && meanDistance(c, next) < jump) candidateCount++ else { candidate = next; candidateCount = 1 }
                if (candidateCount >= confirmFrames) { current = next; candidate = null; candidateCount = 0 }
            }
        }
        return current
    }

    companion object {
        fun meanDistance(a: List<Pt>, b: List<Pt>): Double =
            a.zip(b) { p, q -> hypot(p.x - q.x, p.y - q.y) }.sum() / a.size
    }
}
