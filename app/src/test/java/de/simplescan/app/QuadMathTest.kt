package de.simplescan.app

import de.simplescan.app.data.Pt
import de.simplescan.app.data.QuadMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadMathTest {
    private val tl = Pt(10.0, 12.0); private val tr = Pt(90.0, 8.0)
    private val br = Pt(95.0, 130.0); private val bl = Pt(5.0, 125.0)

    @Test
    fun ordersCornersFromAnyOrder() {
        assertEquals(listOf(tl, tr, br, bl), QuadMath.order(listOf(br, tl, bl, tr)))
        assertEquals(listOf(tl, tr, br, bl), QuadMath.order(listOf(tr, bl, tl, br)))
    }

    @Test
    fun convexAndArea() {
        val q = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 20.0), Pt(0.0, 20.0))
        assertTrue(QuadMath.isConvex(q))
        assertEquals(200.0, QuadMath.area(q), 1e-9)
        assertFalse(QuadMath.isConvex(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(2.0, 2.0), Pt(0.0, 10.0))))
    }

    @Test
    fun plausibility() {
        val page = listOf(tl, tr, br, bl)
        assertTrue(QuadMath.plausible(page, 100, 140))
        // zu klein im Bild
        assertFalse(QuadMath.plausible(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0)), 100, 140))
        // extrem schmal (eher eine Kante)
        assertFalse(QuadMath.plausible(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(100.0, 3.0), Pt(0.0, 3.0)), 100, 10))
    }

    @Test
    fun outputSizeUsesLongerEdges() {
        assertEquals(90 to 122, QuadMath.outputSize(listOf(tl, tr, br, bl)))
    }

    @Test
    fun smoothingMovesPartially() {
        val a = listOf(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 1.0))
        val b = a.map { Pt(it.x + 1, it.y) }
        assertEquals(0.5, QuadMath.smooth(a, b, 0.5)[0].x, 1e-9)
        assertEquals(b, QuadMath.smooth(null, b))
    }
}

class AspectTest {
    // A4-Blatt (210 × 297), 35° nach hinten gekippt, leicht gedreht, mit f = 1500 px auf ein 4000 × 3000-Foto projiziert.
    private val tilted = listOf(
        Pt(1718.961, 1115.674), Pt(2315.378, 1174.239), Pt(2225.605, 1808.518), Pt(1776.359, 1731.004),
    )

    @Test
    fun recoversA4FromTiltedPhoto() {
        val r = QuadMath.trueAspect(tilted, 4000, 3000)!!
        org.junit.Assert.assertEquals(210.0 / 297.0, r, 0.002)
        // Gemessene Kanten allein lägen deutlich daneben (Seite wirkt verkürzt).
        val (mw, mh) = QuadMath.outputSize(tilted)
        org.junit.Assert.assertTrue(mw.toDouble() / mh > 0.85)
    }

    @Test
    fun correctedSizeHasA4Ratio() {
        val (w, h) = QuadMath.correctedSize(tilted, 4000, 3000)
        org.junit.Assert.assertEquals(Math.sqrt(2.0), h.toDouble() / w, 0.01)
    }

    @Test
    fun frontalPhotoKeepsMeasuredRatio() {
        val frontal = listOf(Pt(100.0, 100.0), Pt(310.0, 100.0), Pt(310.0, 397.0), Pt(100.0, 397.0))
        org.junit.Assert.assertEquals(210.0 / 297.0, QuadMath.trueAspect(frontal, 400, 500)!!, 0.002)
        val (w, h) = QuadMath.correctedSize(frontal, 400, 500)
        org.junit.Assert.assertEquals(Math.sqrt(2.0), h.toDouble() / w, 0.01)
    }

    @Test
    fun snapsOnlyNearA4() {
        org.junit.Assert.assertEquals(1 / Math.sqrt(2.0), QuadMath.snapToA4(0.69), 1e-9)
        org.junit.Assert.assertEquals(0.77, QuadMath.snapToA4(0.77), 1e-9) // US-Letter bleibt
        org.junit.Assert.assertEquals(Math.sqrt(2.0), QuadMath.snapToA4(1.40), 1e-9)
    }
}

class ForwardTiltTest {
    // Hochkant-Foto 3000 × 4000, Handy nur nach vorn gekippt (25°): obere und untere Kante bleiben parallel.
    // Brennweite der Kamera 0,75 × 4000 = 3000 px. Gemessene Kanten ergäben 0,88 statt 0,71.
    private val f = 3000.0
    private val quad: List<Pt>
    init {
        val a = Math.toRadians(25.0)
        fun p(x: Double, y: Double): Pt {
            val yy = y * Math.cos(a); val z = y * Math.sin(a) + 450
            return Pt(1500 + f * x / z, 2000 + f * yy / z)
        }
        quad = listOf(p(-105.0, -148.5), p(105.0, -148.5), p(105.0, 148.5), p(-105.0, 148.5))
    }

    @Test
    fun measuredEdgesAreWrong() {
        val (w, h) = QuadMath.outputSize(quad)
        org.junit.Assert.assertTrue(w.toDouble() / h > 0.85)
    }

    @Test
    fun knownFocalGivesA4() {
        org.junit.Assert.assertEquals(210.0 / 297.0, QuadMath.trueAspect(quad, 3000, 4000, f)!!, 0.002)
    }

    @Test
    fun typicalFocalIsCloseEnoughForA4() {
        // ohne Kameradaten: typischer Wert 0,75 × lange Kante = genau dieser Fall
        val (w, h) = QuadMath.correctedSize(quad, 3000, 4000)
        org.junit.Assert.assertEquals(Math.sqrt(2.0), h.toDouble() / w, 0.01)
    }
}

class OrderRobustTest {
    @Test
    fun duplicatePointsDoNotCrash() {
        // Zwei Ecken auf demselben Punkt: früher NoSuchElementException → gar kein Rahmen
        val p = Pt(10.0, 10.0)
        val r = QuadMath.order(listOf(p, Pt(10.0, 10.0), Pt(90.0, 80.0), Pt(5.0, 70.0)))
        assertEquals(4, r.size)
        val same = QuadMath.order(listOf(p, p, p, p))
        assertEquals(4, same.size)
    }

    @Test
    fun rotatedSquareStartsTopLeft() {
        val r = QuadMath.order(listOf(Pt(50.0, 0.0), Pt(100.0, 50.0), Pt(50.0, 100.0), Pt(0.0, 50.0)))
        assertEquals(4, r.size)
        // im Uhrzeigersinn (Bild, y nach unten)
        assertTrue(QuadMath.isConvex(r))
    }
}

class AgreesTest {
    private val live = listOf(Pt(340.0, 930.0), Pt(822.0, 876.0), Pt(905.0, 1570.0), Pt(195.0, 1535.0))

    @Test
    fun smallDeviationAgrees() {
        val photo = live.map { Pt(it.x + 15, it.y - 10) }
        assertTrue(QuadMath.agrees(photo, live, 1200, 1600))
    }

    @Test
    fun skewedIntoReflectionDisagrees() {
        // Fall vom 27.11.: obere Kante gleich, unten in den Lichtfleck gezogen
        val photo = listOf(Pt(338.0, 903.0), Pt(820.0, 870.0), Pt(1112.0, 1755.0), Pt(518.0, 1925.0))
        org.junit.Assert.assertFalse(QuadMath.agrees(photo, live, 1200, 1600))
    }
}
