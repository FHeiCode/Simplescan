package de.simplescan.app.data

/** Ein hochgeladener Scan mit seinem Link. */
data class RemoteLink(val name: String, val link: String, val needsLogin: Boolean)

/** Betreff und Text beim Versenden (ohne Android, darum per Unit-Test prüfbar). */
object ShareText {

    fun subject(names: List<String>): String = when (names.size) {
        0 -> "Scan"
        1 -> names[0]
        else -> "${names.size} Scans"
    }

    /** [attached]: Namen der angehängten PDFs, [links]: hochgeladene Scans. */
    fun body(attached: List<String>, links: List<RemoteLink>): String {
        val parts = mutableListOf<String>()
        when (attached.size) {
            0 -> Unit
            1 -> parts += "Anbei der Scan „${attached[0]}“."
            else -> parts += "Anbei ${attached.size} Scans:\n" + attached.joinToString("\n") { "- $it" }
        }
        if (links.size == 1 && attached.isEmpty()) {
            parts += "Hier ist der Scan „${links[0].name}“:\n${links[0].link}"
        } else if (links.isNotEmpty()) {
            parts += "Links:\n" + links.joinToString("\n") { "- ${it.name}: ${it.link}" }
        }
        if (links.any { it.needsLogin }) {
            parts += "(Zum Öffnen der Links sind Zugangsdaten für den Server nötig.)"
        }
        return parts.joinToString("\n\n")
    }
}
