package de.simplescan.app.data

/** Feste Empfänger-Adressen: einlesen, prüfen, anzeigen (ohne Android, darum per Unit-Test prüfbar). */
object EmailList {
    private val pattern = Regex("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;.]{2,}$")

    /** Zerlegt „a@b.de, c@d.de; e@f.de“ in einzelne Adressen (doppelte fallen weg). */
    fun split(input: String): List<String> =
        input.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    /** Erste ungültige Adresse oder null, wenn alles passt. */
    fun firstInvalid(input: String): String? = split(input).firstOrNull { !pattern.matches(it) }

    /** Gespeicherte Form: durch „, “ getrennt. */
    fun normalize(input: String): String = split(input).joinToString(", ")

    /** Für die mailto-Adresse: „a@b.de,c@d.de“ (das @ bleibt lesbar). */
    fun mailtoRecipients(addresses: List<String>): String =
        addresses.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8").replace("%40", "@").replace("+", "%20") }
}
