package de.simplescan.app.data

/** Bildfilter für eine gescannte Seite. */
enum class PageFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCE("Verbessern"),
    GRAY("Graustufen"),
    BW("Schwarzweiß");

    /** Nächster Filter (zum Durchschalten per Tipp). */
    fun next(): PageFilter = entries[(ordinal + 1) % entries.size]
}
