package de.simplescan.app.data

import org.json.JSONObject

/** Liest die Antwort der Nextcloud-Freigabe-Schnittstelle (tolerant, ohne Absturz bei Unerwartetem). */
object ShareParser {
    private fun data(json: String): Any? = runCatching { JSONObject(json).getJSONObject("ocs").opt("data") }.getOrNull()

    fun url(json: String): String? {
        val d = data(json)
        val obj = when (d) {
            is JSONObject -> d
            is org.json.JSONArray -> d.optJSONObject(0)
            else -> null
        } ?: return null
        return obj.optString("url", "").takeIf { it.startsWith("http") }
    }

    fun message(json: String): String? =
        runCatching { JSONObject(json).getJSONObject("ocs").getJSONObject("meta").optString("message", "") }
            .getOrNull()?.ifBlank { null }
}
