package de.simplescan.app

import de.simplescan.app.data.DavKind
import de.simplescan.app.data.WebDavPaths
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavPathsTest {

    @Test
    fun nextcloudBaseFromPlainAddress() {
        assertEquals(
            "https://cloud.example.de/remote.php/dav/files/anna/",
            WebDavPaths.baseUrl(DavKind.NEXTCLOUD, "cloud.example.de", "anna"),
        )
        assertEquals(
            "https://example.com/nextcloud/remote.php/dav/files/anna/",
            WebDavPaths.baseUrl(DavKind.NEXTCLOUD, " https://example.com/nextcloud/ ", "anna"),
        )
    }

    @Test
    fun nextcloudKeepsFullDavAddress() {
        assertEquals(
            "https://cloud.example.de/remote.php/dav/files/A1B2/",
            WebDavPaths.baseUrl(DavKind.NEXTCLOUD, "https://cloud.example.de/remote.php/dav/files/A1B2", "anna@mail.de"),
        )
    }

    @Test
    fun nextcloudEncodesUserName() {
        assertEquals(
            "https://c.de/remote.php/dav/files/anna%40mail.de/",
            WebDavPaths.baseUrl(DavKind.NEXTCLOUD, "c.de", "anna@mail.de"),
        )
    }

    @Test
    fun plainWebDavOnlyAddsSchemeAndSlash() {
        assertEquals("https://dav.box.de/webdav/", WebDavPaths.baseUrl(DavKind.WEBDAV, "dav.box.de/webdav", "x"))
        assertEquals("http://192.168.1.5:8080/dav/", WebDavPaths.baseUrl(DavKind.WEBDAV, "http://192.168.1.5:8080/dav/", "x"))
    }

    @Test
    fun foldersAndFilesAreEncoded() {
        val base = "https://c.de/remote.php/dav/files/anna/"
        assertEquals(listOf("Scans", "2026"), WebDavPaths.folderSegments("/Scans//2026/"))
        assertEquals(listOf("a"), WebDavPaths.folderSegments("../a/."))
        assertEquals("${base}Meine%20Scans/", WebDavPaths.folderUrl(base, "Meine Scans"))
        assertEquals(base, WebDavPaths.folderUrl(base, ""))
        assertEquals("${base}Scans/Rechnung%20M%C3%A4rz.pdf", WebDavPaths.fileUrl(base, "Scans", "Rechnung März.pdf"))
    }

    @Test
    fun candidateNames() {
        assertEquals("Scan.pdf", WebDavPaths.candidateName("Scan", 0))
        assertEquals("Scan (2).pdf", WebDavPaths.candidateName("Scan", 2))
    }

    @Test
    fun labels() {
        assertEquals("Nextcloud · cloud.example.de/Scans", WebDavPaths.label(DavKind.NEXTCLOUD, "https://cloud.example.de/", "/Scans/"))
        assertEquals("WebDAV · dav.box.de", WebDavPaths.label(DavKind.WEBDAV, "dav.box.de/webdav", ""))
    }
}
