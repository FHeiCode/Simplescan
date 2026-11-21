package de.simplescan.app

import de.simplescan.app.data.Pt
import de.simplescan.app.data.QuadMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateTest {
    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = listOf(Pt(x0, y0), Pt(x1, y0), Pt(x1, y1), Pt(x0, y1))

    @Test
    fun businessCardIsBigEnough() {
        // Visitenkarte, ~5 % des Bildes: früher abgelehnt (Mindestgröße 15 %), jetzt erlaubt (ab 4 %)
        assertTrue(QuadMath.plausibleCandidate(rect(400.0, 600.0, 800.0, 850.0), 1200, 1600))
        assertFalse(QuadMath.plausible(rect(400.0, 600.0, 800.0, 850.0), 1200, 1600))
    }

    @Test
    fun rejectsTinyFlatAndFullFrame() {
        assertFalse(QuadMath.plausibleCandidate(rect(0.0, 0.0, 100.0, 100.0), 1200, 1600)) // zu klein
        assertFalse(QuadMath.plausibleCandidate(rect(100.0, 100.0, 1100.0, 200.0), 1200, 1600)) // Strich
        assertFalse(QuadMath.plausibleCandidate(rect(0.0, 0.0, 1199.0, 1599.0), 1200, 1600)) // ganzes Bild
        // eine Seite läuft am linken Bildrand entlang (Tisch bis zum Rand, wie im Bildschirmfoto)
        assertFalse(QuadMath.plausibleCandidate(listOf(Pt(0.0, 80.0), Pt(1000.0, 190.0), Pt(1160.0, 1270.0), Pt(0.0, 1310.0)), 1200, 1600))
        // nur eine Ecke nah am Rand ist erlaubt
        assertTrue(QuadMath.plausibleCandidate(listOf(Pt(5.0, 200.0), Pt(1000.0, 190.0), Pt(1100.0, 1270.0), Pt(200.0, 1310.0)), 1200, 1600))
    }

    @Test
    fun rejectsVeryPointyCorners() {
        // Parallelogramm mit ~20°-Ecken
        val q = listOf(Pt(100.0, 100.0), Pt(700.0, 100.0), Pt(1100.0, 300.0), Pt(500.0, 300.0))
        assertFalse(QuadMath.plausibleCandidate(q, 1200, 1600))
    }

    @Test
    fun insideDetectsTableInPage() {
        val page = listOf(Pt(100.0, 120.0), Pt(900.0, 80.0), Pt(950.0, 1300.0), Pt(120.0, 1350.0))
        val table = rect(200.0, 600.0, 800.0, 900.0)
        assertTrue(QuadMath.inside(table, page))
        assertFalse(QuadMath.inside(page, table))
        // gleiche Prüfung bei umgekehrtem Umlaufsinn
        assertTrue(QuadMath.inside(table, page.reversed()))
    }

    @Test
    fun sidePointsPerSide() {
        val sides = QuadMath.sidePoints(rect(0.0, 0.0, 400.0, 400.0), step = 4.0)
        assertEquals(4, sides.size)
        assertTrue(sides.all { it.size == 100 })
        assertTrue(sides[0].all { it.y == 0.0 }) // obere Seite
    }

    @Test
    fun foldedLetterPanelsMerge() {
        // oberes und mittleres Drittel eines Briefs teilen sich die Knicklinie
        val top = listOf(Pt(100.0, 100.0), Pt(900.0, 90.0), Pt(905.0, 500.0), Pt(102.0, 505.0))
        val mid = listOf(Pt(103.0, 507.0), Pt(904.0, 498.0), Pt(910.0, 900.0), Pt(105.0, 910.0))
        val all = QuadMath.mergeAlongSharedSide(top, mid, 20.0)!!
        assertEquals(listOf(Pt(100.0, 100.0), Pt(900.0, 90.0), Pt(910.0, 900.0), Pt(105.0, 910.0)), all)
        assertNull(QuadMath.mergeAlongSharedSide(top, rect(1000.0, 1000.0, 1100.0, 1100.0), 20.0))
    }

    @Test
    fun intersectLines() {
        val p = QuadMath.intersect(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(5.0, -3.0), Pt(0.0, 1.0))!!
        assertEquals(5.0, p.x, 1e-9); assertEquals(0.0, p.y, 1e-9)
        assertNull(QuadMath.intersect(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(0.0, 2.0), Pt(2.0, 0.0)))
    }
}
