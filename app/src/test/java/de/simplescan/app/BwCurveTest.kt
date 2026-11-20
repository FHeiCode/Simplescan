package de.simplescan.app

import de.simplescan.app.data.BwCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BwCurveTest {
    private fun at(table: ByteArray, v: Int) = table[v].toInt() and 0xFF

    @Test
    fun blackBelowWhiteAboveSmoothBetween() {
        val t = BwCurve.table(150.0) // schwarz bis 105, weiß ab 145
        assertEquals(0, at(t, 100))
        assertEquals(0, at(t, 105))
        assertEquals(255, at(t, 145))
        assertEquals(255, at(t, 150)) // Grau an der Schwelle wird weiß → schlanke Striche
        assertEquals(255, at(t, 250))
        val mid = at(t, 125)
        assertTrue(mid in 100..200)
        // gleichmäßig steigend
        for (v in 1..255) assertTrue(at(t, v) >= at(t, v - 1))
    }

    @Test
    fun thresholdIsClamped() {
        val t = BwCurve.table(250.0) // sonst würde fast alles schwarz
        assertEquals(255, at(t, 245))
    }
}
