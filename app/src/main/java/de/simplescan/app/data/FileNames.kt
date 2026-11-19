package de.simplescan.app.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Reine Hilfsfunktionen ohne Android-Abhängigkeit (darum per Unit-Test prüfbar). */
object FileNames {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val forbidden = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private const val MAX_LENGTH = 100

    /** Vorschlag für einen neuen Scan, z. B. „Scan_2026-11-19_08-50-12“. */
    fun suggestion(now: LocalDateTime = LocalDateTime.now()): String = "Scan_" + now.format(stamp)

    /**
     * Macht aus einer Eingabe einen gültigen Dateinamen ohne Endung.
     * Verbotene Zeichen werden zu „_“, eine eingetippte „.pdf“-Endung fällt weg,
     * leere Eingaben ergeben den Vorschlag [fallback].
     */
    fun sanitize(input: String, fallback: String): String {
        var name = input.trim()
        if (name.endsWith(".pdf", ignoreCase = true)) name = name.dropLast(4)
        name = name.replace(forbidden, "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
        if (name.length > MAX_LENGTH) name = name.take(MAX_LENGTH).trimEnd(' ', '.')
        return name.ifBlank { fallback }
    }

    /**
     * Lesbarer Name eines gewählten Ordners aus der Dokument-ID des Ordnerauswahl-Dialogs,
     * z. B. „primary:Documents/Scans“ → „Documents/Scans“, „primary:“ → „Interner Speicher“.
     */
    fun folderLabel(treeDocumentId: String?): String {
        if (treeDocumentId.isNullOrBlank()) return "Gewählter Ordner"
        val volume = treeDocumentId.substringBefore(':')
        val path = treeDocumentId.substringAfter(':', "").trim('/')
        val root = if (volume == "primary") "Interner Speicher" else "SD-Karte"
        return path.ifBlank { root }
    }
}
