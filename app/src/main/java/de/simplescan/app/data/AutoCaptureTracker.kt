package de.simplescan.app.data

/**
 * Entscheidet, wann automatisch ausgelöst wird (ohne Android, darum per Unit-Test prüfbar):
 * Der (beruhigte) Rahmen muss [steadyMs] lang ruhig liegen (Abweichung unter [tolerance]).
 * Danach erst wieder, wenn die Seite [rearmMissingMs] lang weg war oder ein deutlich anderes
 * Viereck erscheint ([rearmJump]) – so wird dieselbe Seite nicht doppelt fotografiert.
 */
class AutoCaptureTracker(
    private val steadyMs: Long = 1200,
    private val tolerance: Double = 0.015,
    private val rearmMissingMs: Long = 600,
    private val rearmJump: Double = 0.2,
) {
    private var anchor: List<Pt>? = null
    private var since = 0L
    private var armed = true
    private var missingSince: Long? = null
    private var lastFired: List<Pt>? = null

    /** Wird gerade auf Ruhe gewartet (für den Hinweis „Ruhig halten …“)? */
    val steadying: Boolean get() = armed && anchor != null

    /** Nach einem Foto von Hand: dieselbe Seite nicht gleich noch einmal automatisch. */
    fun disarm(current: List<Pt>?) {
        armed = false
        lastFired = current
        anchor = null
    }

    fun update(quad: List<Pt>?, now: Long): Boolean {
        if (quad == null) {
            anchor = null
            val ms = missingSince ?: now.also { missingSince = it }
            if (!armed && now - ms >= rearmMissingMs) armed = true
            return false
        }
        missingSince = null
        if (!armed) {
            val lf = lastFired
            if (lf == null || QuadStabilizer.meanDistance(lf, quad) > rearmJump) armed = true else return false
        }
        val a = anchor
        if (a == null || QuadStabilizer.meanDistance(a, quad) > tolerance) {
            anchor = quad
            since = now
            return false
        }
        if (now - since >= steadyMs) {
            disarm(quad)
            return true
        }
        return false
    }
}
