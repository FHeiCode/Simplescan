package de.simplescan.app

import de.simplescan.app.data.PdfPages
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPagesTest {
    @Test
    fun pageKeepsAspectRatioAtA4Width() {
        assertEquals(595 to 842, PdfPages.pageSize(2480, 3508))
        assertEquals(595 to 446, PdfPages.pageSize(4000, 3000))
    }

    @Test
    fun downscaleOnlyShrinks() {
        assertEquals(0.5, PdfPages.downscale(4000, 3000, 2000), 1e-9)
        assertEquals(1.0, PdfPages.downscale(1200, 1600, 2000), 1e-9)
    }
}
