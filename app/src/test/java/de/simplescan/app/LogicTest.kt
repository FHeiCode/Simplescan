package de.simplescan.app

import de.simplescan.app.data.FileNames
import de.simplescan.app.data.SavedScan
import de.simplescan.app.data.ScanListCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LogicTest {

    @Test
    fun suggestionHasDateAndTime() {
        assertEquals("Scan_2026-11-19_08-50-07", FileNames.suggestion(LocalDateTime.of(2026, 11, 19, 8, 50, 7)))
    }

    @Test
    fun sanitizeRemovesForbiddenCharsAndExtension() {
        assertEquals("Rechnung_Strom_2026", FileNames.sanitize("  Rechnung/Strom:2026.PDF ", "x"))
        assertEquals("a_b_c_d", FileNames.sanitize("a?b*c|d", "x"))
        assertEquals("Mietvertrag Seite 1", FileNames.sanitize("Mietvertrag   Seite 1", "x"))
    }

    @Test
    fun sanitizeFallsBackWhenEmpty() {
        assertEquals("Scan_1", FileNames.sanitize("   ", "Scan_1"))
        assertEquals("Scan_1", FileNames.sanitize(".pdf", "Scan_1"))
        assertEquals("Scan_1", FileNames.sanitize("...", "Scan_1"))
    }

    @Test
    fun sanitizeLimitsLength() {
        assertTrue(FileNames.sanitize("a".repeat(300), "x").length <= 100)
    }

    @Test
    fun folderLabels() {
        assertEquals("Documents/Scans", FileNames.folderLabel("primary:Documents/Scans"))
        assertEquals("Interner Speicher", FileNames.folderLabel("primary:"))
        assertEquals("SD-Karte", FileNames.folderLabel("1A2B-3C4D:"))
        assertEquals("Gewählter Ordner", FileNames.folderLabel(null))
    }

    @Test
    fun recentListRoundTripAndTolerantDecode() {
        val list = listOf(SavedScan("a.pdf", "content://x/1", "Download-Ordner", 2, 123L))
        assertEquals(list, ScanListCodec.decode(ScanListCodec.encode(list)))
        assertEquals(emptyList<SavedScan>(), ScanListCodec.decode("kaputt"))
        assertEquals(1, ScanListCodec.decode("""[{"uri":"content://y"}, 5, {"name":"ohne uri"}]""").size)
    }

    @Test
    fun recentListIsCapped() {
        val many = (1..50).map { SavedScan("$it.pdf", "content://x/$it", "", 1, it.toLong()) }
        assertEquals(ScanListCodec.MAX_ENTRIES, ScanListCodec.decode(ScanListCodec.encode(many)).size)
    }
}
