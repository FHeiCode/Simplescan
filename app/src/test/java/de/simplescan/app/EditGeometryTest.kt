package de.simplescan.app

import de.simplescan.app.data.EditGeometry
import de.simplescan.app.data.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditGeometryTest {
    @Test
    fun moveWithinBounds() {
        assertEquals(listOf("b", "a", "c"), EditGeometry.move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("a", "c", "b"), EditGeometry.move(listOf("a", "b", "c"), 1, 1))
        assertEquals(listOf("a", "b", "c"), EditGeometry.move(listOf("a", "b", "c"), 2, 1))
        assertEquals(listOf("a", "b", "c"), EditGeometry.move(listOf("a", "b", "c"), 7, -1))
    }

    @Test
    fun rotateRightWraps() {
        assertEquals(90, EditGeometry.rotateRight(0))
        assertEquals(0, EditGeometry.rotateRight(270))
    }

    @Test
    fun fitAndBackAgain() {
        val fit = EditGeometry.fit(1600, 1200, 800.0, 1000.0) // Bild breiter → oben/unten Rand
        assertEquals(0.5, fit.scale, 1e-9)
        assertEquals(200.0, fit.offsetY, 1e-9)
        val v = fit.toView(Pt(400.0, 600.0))
        assertEquals(Pt(200.0, 500.0), v)
        assertEquals(Pt(400.0, 600.0), fit.toImage(v.x, v.y))
    }

    @Test
    fun hitsNearestCornerOnly() {
        val corners = listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(100.0, 100.0), Pt(0.0, 100.0))
        assertEquals(2, EditGeometry.hitCorner(corners, 95.0, 90.0, 30.0))
        assertNull(EditGeometry.hitCorner(corners, 50.0, 50.0, 30.0))
    }

    @Test
    fun clampKeepsInside() {
        assertEquals(Pt(0.0, 99.0), EditGeometry.clamp(Pt(-5.0, 300.0), 50, 100))
    }
}
