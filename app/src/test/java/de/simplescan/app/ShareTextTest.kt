package de.simplescan.app

import de.simplescan.app.data.RemoteLink
import de.simplescan.app.data.ShareText
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
    @Test
    fun subjects() {
        assertEquals("a.pdf", ShareText.subject(listOf("a.pdf")))
        assertEquals("3 Scans", ShareText.subject(listOf("a", "b", "c")))
    }

    @Test
    fun singleAttachment() {
        assertEquals("Anbei der Scan „a.pdf“.", ShareText.body(listOf("a.pdf"), emptyList()))
    }

    @Test
    fun singleLink() {
        assertEquals(
            "Hier ist der Scan „b.pdf“:\nhttps://c.de/s/X",
            ShareText.body(emptyList(), listOf(RemoteLink("b.pdf", "https://c.de/s/X", false))),
        )
    }

    @Test
    fun mixedWithLoginHint() {
        val body = ShareText.body(
            listOf("a.pdf", "b.pdf"),
            listOf(RemoteLink("c.pdf", "https://dav/c.pdf", true)),
        )
        assertEquals(
            "Anbei 2 Scans:\n- a.pdf\n- b.pdf\n\nLinks:\n- c.pdf: https://dav/c.pdf\n\n(Zum Öffnen der Links sind Zugangsdaten für den Server nötig.)",
            body,
        )
    }
}
