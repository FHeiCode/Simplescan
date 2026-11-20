package de.simplescan.app

import de.simplescan.app.data.Pt
import de.simplescan.app.data.QuadStabilizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuadStabilizerTest {
    private fun quad(dx: Double = 0.0) = listOf(Pt(0.2 + dx, 0.2), Pt(0.8 + dx, 0.2), Pt(0.8 + dx, 0.8), Pt(0.2 + dx, 0.8))

    @Test
    fun showsAfterTwoHitsAndIgnoresTinyJitter() {
        val s = QuadStabilizer()
        assertNull(s.update(quad()))
        val shown = s.update(quad())!!
        assertEquals(shown, s.update(quad(0.003))) // Wackler unter der Schwelle
    }

    @Test
    fun smallMovesAreSmoothed() {
        val s = QuadStabilizer(alpha = 0.25)
        s.update(quad()); s.update(quad())
        val moved = s.update(quad(0.04))!!
        assertEquals(0.21, moved[0].x, 1e-9)
    }

    @Test
    fun bigJumpNeedsConfirmation() {
        val s = QuadStabilizer()
        s.update(quad()); s.update(quad())
        assertEquals(0.2, s.update(quad(0.3))!![0].x, 1e-9)
        assertEquals(0.2, s.update(quad(0.3))!![0].x, 1e-9)
        assertEquals(0.5, s.update(quad(0.3))!![0].x, 1e-9)
    }

    @Test
    fun staysVisibleThroughShortDropouts() {
        val s = QuadStabilizer(hideAfter = 8)
        s.update(quad()); s.update(quad())
        repeat(7) { assertNotNull(s.update(null)) }
        assertNull(s.update(null))
    }
}
