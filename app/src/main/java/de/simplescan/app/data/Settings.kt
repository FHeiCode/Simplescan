package de.simplescan.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "simplescan")

/** Wohin PDFs gespeichert werden. */
sealed interface Target {
    val label: String

    data object Downloads : Target {
        override val label = "Download-Ordner"
    }

    data class Folder(val treeUri: String, override val label: String) : Target

    /** Nextcloud oder anderer WebDAV-Server. Das Passwort liegt verschlüsselt extra (siehe SecretBox). */
    data class WebDav(
        val kind: DavKind,
        val server: String,
        val user: String,
        val folder: String,
    ) : Target {
        override val label: String get() = WebDavPaths.label(kind, server, folder)
    }
}

/** Ein bereits gespeichertes PDF für die Liste „Zuletzt gespeichert“. */
data class SavedScan(
    val name: String,
    val uri: String,
    val location: String,
    val pages: Int,
    val savedAt: Long,
    /** Öffentlicher Nextcloud-Link, einmal erstellt und dann wiederverwendet. */
    val shareUrl: String? = null,
)

object ScanListCodec {
    const val MAX_ENTRIES = 30

    fun encode(list: List<SavedScan>): String = JSONArray().apply {
        list.take(MAX_ENTRIES).forEach {
            put(
                JSONObject()
                    .put("name", it.name)
                    .put("uri", it.uri)
                    .put("location", it.location)
                    .put("pages", it.pages)
                    .put("savedAt", it.savedAt)
                    .apply { it.shareUrl?.let { url -> put("shareUrl", url) } }
            )
        }
    }.toString()

    /** Tolerant: kaputte Einträge werden übersprungen statt abzustürzen. */
    fun decode(json: String?): List<SavedScan> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri")
            if (uri.isBlank()) return@mapNotNull null
            SavedScan(
                name = o.optString("name", "Scan"),
                uri = uri,
                location = o.optString("location", ""),
                pages = o.optInt("pages", 0),
                savedAt = o.optLong("savedAt", 0L),
                shareUrl = o.optString("shareUrl", "").ifBlank { null },
            )
        }
    }
}

class SettingsStore(private val context: Context) {
    private val keyTarget = stringPreferencesKey("target")
    private val keyTreeUri = stringPreferencesKey("tree_uri")
    private val keyTreeLabel = stringPreferencesKey("tree_label")
    private val keyRecent = stringPreferencesKey("recent")
    private val keyDefaultEmail = stringPreferencesKey("default_email")
    private val keyDavKind = stringPreferencesKey("dav_kind")
    private val keyDavServer = stringPreferencesKey("dav_server")
    private val keyDavUser = stringPreferencesKey("dav_user")
    private val keyDavFolder = stringPreferencesKey("dav_folder")
    private val keyDavPassword = stringPreferencesKey("dav_password")

    val target: Flow<Target> = context.dataStore.data.map { p ->
        val uri = p[keyTreeUri]
        when {
            p[keyTarget] == "folder" && !uri.isNullOrBlank() -> Target.Folder(uri, p[keyTreeLabel] ?: "Gewählter Ordner")
            p[keyTarget] == "webdav" && davFrom(p) != null -> davFrom(p)!!
            else -> Target.Downloads
        }
    }

    /** Zuletzt eingetragene WebDAV-Daten (auch wenn gerade ein anderes Ziel aktiv ist), zum Vorbefüllen. */
    val lastWebDav: Flow<Target.WebDav?> = context.dataStore.data.map { davFrom(it) }

    private fun davFrom(p: androidx.datastore.preferences.core.Preferences): Target.WebDav? {
        val server = p[keyDavServer]
        val user = p[keyDavUser]
        if (server.isNullOrBlank() || user.isNullOrBlank()) return null
        val kind = runCatching { DavKind.valueOf(p[keyDavKind] ?: "") }.getOrDefault(DavKind.NEXTCLOUD)
        return Target.WebDav(kind, server, user, p[keyDavFolder] ?: "")
    }

    suspend fun webDavPassword(): String? = SecretBox.decrypt(context.dataStore.data.first()[keyDavPassword])

    val recent: Flow<List<SavedScan>> = context.dataStore.data.map { ScanListCodec.decode(it[keyRecent]) }

    suspend fun setTarget(target: Target) {
        context.dataStore.edit { p ->
            when (target) {
                Target.Downloads -> p[keyTarget] = "downloads"
                is Target.Folder -> {
                    p[keyTarget] = "folder"
                    p[keyTreeUri] = target.treeUri
                    p[keyTreeLabel] = target.label
                }
                is Target.WebDav -> p[keyTarget] = "webdav"
            }
        }
    }

    /** Speichert die WebDAV-Daten und macht sie zum aktiven Ziel. */
    suspend fun setWebDav(target: Target.WebDav, password: String) {
        val encrypted = SecretBox.encrypt(password)
        context.dataStore.edit { p ->
            p[keyDavKind] = target.kind.name
            p[keyDavServer] = target.server
            p[keyDavUser] = target.user
            p[keyDavFolder] = target.folder
            p[keyDavPassword] = encrypted
            p[keyTarget] = "webdav"
        }
    }

    suspend fun addRecent(scan: SavedScan) {
        context.dataStore.edit { p ->
            val list = ScanListCodec.decode(p[keyRecent]).filterNot { it.uri == scan.uri }
            p[keyRecent] = ScanListCodec.encode(listOf(scan) + list)
        }
    }

    /** Feste Empfänger für den E-Mail-Versand, „“ = keine. */
    val defaultEmail: Flow<String> = context.dataStore.data.map { it[keyDefaultEmail].orEmpty() }

    suspend fun setDefaultEmail(addresses: String) {
        context.dataStore.edit { it[keyDefaultEmail] = EmailList.normalize(addresses) }
    }

    suspend fun setShareUrl(uri: String, shareUrl: String) {
        context.dataStore.edit { p ->
            p[keyRecent] = ScanListCodec.encode(
                ScanListCodec.decode(p[keyRecent]).map { if (it.uri == uri) it.copy(shareUrl = shareUrl) else it }
            )
        }
    }

    suspend fun removeRecent(uri: String) = removeRecent(setOf(uri))

    suspend fun removeRecent(uris: Set<String>) {
        if (uris.isEmpty()) return
        context.dataStore.edit { p ->
            p[keyRecent] = ScanListCodec.encode(ScanListCodec.decode(p[keyRecent]).filterNot { it.uri in uris })
        }
    }
}
