package de.simplescan.app

import de.simplescan.app.data.AutoCaptureTracker
import de.simplescan.app.data.PageFilter
import de.simplescan.app.data.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureTest {
    private fun quad(dx: Double = 0.0) = listOf(Pt(0.2 + dx, 0.2), Pt(0.8 + dx, 0.2), Pt(0.8 + dx, 0.8), Pt(0.2 + dx, 0.8))

    @Test
    fun firesAfterSteadyTimeOnlyOnce() {
        val t = AutoCaptureTracker(steadyMs = 1000)
        assertFalse(t.update(quad(), 0))
        assertFalse(t.update(quad(0.005), 500))
        assertTrue(t.update(quad(), 1000))
        // gleiche Seite bleibt liegen → kein zweites Foto
        assertFalse(t.update(quad(), 3000))
        assertFalse(t.update(quad(), 6000))
    }

    @Test
    fun movementRestartsTheClock() {
        val t = AutoCaptureTracker(steadyMs = 1000)
        t.update(quad(), 0)
        assertFalse(t.update(quad(0.05), 900)) // bewegt → neu anfangen
        assertFalse(t.update(quad(0.05), 1500))
        assertTrue(t.update(quad(0.05), 1900))
    }

    @Test
    fun rearmsAfterPageWasGone() {
        val t = AutoCaptureTracker(steadyMs = 1000, rearmMissingMs = 600)
        t.update(quad(), 0); assertTrue(t.update(quad(), 1000))
        t.update(null, 1100); t.update(null, 1800) // Seite weg
        assertFalse(t.update(quad(), 1900))
        assertTrue(t.update(quad(), 2900))
    }

    @Test
    fun manualShotDisarms() {
        val t = AutoCaptureTracker(steadyMs = 1000)
        t.update(quad(), 0)
        t.disarm(quad())
        assertFalse(t.update(quad(), 5000))
    }

    @Test
    fun filterCycles() {
        assertEquals(PageFilter.ENHANCE, PageFilter.ORIGINAL.next())
        assertEquals(PageFilter.ORIGINAL, PageFilter.BW.next())
    }
}
