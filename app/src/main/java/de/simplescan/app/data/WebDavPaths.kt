package de.simplescan.app.data

import java.net.URLEncoder

/** Art des Servers: Bei Nextcloud reicht die normale Adresse, der WebDAV-Pfad wird ergänzt. */
enum class DavKind { NEXTCLOUD, WEBDAV }

/** Reine Hilfsfunktionen für WebDAV-Adressen (ohne Android, darum per Unit-Test prüfbar). */
object WebDavPaths {

    /**
     * Basisadresse mit „/“ am Ende.
     * Nextcloud: „cloud.example.de“ → „https://cloud.example.de/remote.php/dav/files/<user>/“.
     * Steht „remote.php“ schon in der Adresse, bleibt sie, wie sie ist.
     * WebDAV: die Adresse wird nur um „https://“ und „/“ ergänzt.
     */
    fun baseUrl(kind: DavKind, server: String, user: String): String {
        var s = server.trim()
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            s = "https://$s"
        }
        s = s.trimEnd('/')
        if (kind == DavKind.NEXTCLOUD && !s.contains("/remote.php/", ignoreCase = true) &&
            !s.endsWith("/remote.php", ignoreCase = true)
        ) {
            s = "$s/remote.php/dav/files/${encodeSegment(user.trim())}"
        }
        return "$s/"
    }

    /** Ordnerpfad in einzelne, bereinigte Teile zerlegen: „/Scans//2026/“ → [Scans, 2026]. */
    fun folderSegments(folder: String): List<String> =
        folder.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }

    /** Adresse eines Ordners (mit „/“ am Ende). */
    fun folderUrl(base: String, folder: String): String =
        base + folderSegments(folder).joinToString("") { encodeSegment(it) + "/" }

    /** Adresse einer Datei im Ordner. */
    fun fileUrl(base: String, folder: String, fileName: String): String =
        folderUrl(base, folder) + encodeSegment(fileName)

    /** Namen, die nacheinander probiert werden, wenn die Datei schon existiert: „a.pdf“, „a (1).pdf“, … */
    fun candidateName(baseName: String, attempt: Int): String =
        if (attempt == 0) "$baseName.pdf" else "$baseName ($attempt).pdf"

    /** Kurze Anzeige für den Speicherort, z. B. „Nextcloud · cloud.example.de/Scans“. */
    fun label(kind: DavKind, server: String, folder: String): String {
        val host = server.trim()
            .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .substringBefore('/')
        val path = folderSegments(folder).joinToString("/")
        val prefix = if (kind == DavKind.NEXTCLOUD) "Nextcloud" else "WebDAV"
        return if (path.isEmpty()) "$prefix · $host" else "$prefix · $host/$path"
    }

    /**
     * Nextcloud-Adresse (ohne „/remote.php/…“) aus der Adresse einer hochgeladenen Datei,
     * z. B. „https://c.de/nc/remote.php/dav/files/anna/Scans/a.pdf“ → „https://c.de/nc“. Null, wenn kein Nextcloud-Pfad.
     */
    fun nextcloudRoot(fileUrl: String): String? =
        fileUrl.indexOf("/remote.php/").takeIf { it > 0 }?.let { fileUrl.substring(0, it) }

    /**
     * Pfad der Datei innerhalb der Nextcloud-Dateien des Benutzers, wie ihn die Freigabe-Schnittstelle erwartet,
     * z. B. „/Scans/Rechnung März.pdf“. Null, wenn die Adresse kein Nextcloud-Dateipfad ist.
     */
    fun nextcloudUserPath(fileUrl: String): String? {
        val rest = when {
            fileUrl.contains("/remote.php/dav/files/") ->
                fileUrl.substringAfter("/remote.php/dav/files/").substringAfter('/', "")
            fileUrl.contains("/remote.php/webdav/") -> fileUrl.substringAfter("/remote.php/webdav/")
            else -> return null
        }
        if (rest.isBlank()) return null
        return "/" + rest.split('/').filter { it.isNotEmpty() }.joinToString("/") { decodeSegment(it) }
    }

    fun decodeSegment(s: String): String = java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    fun encodeSegment(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
