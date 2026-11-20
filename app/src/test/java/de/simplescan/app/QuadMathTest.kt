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
