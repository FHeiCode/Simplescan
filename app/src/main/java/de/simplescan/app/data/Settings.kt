package de.simplescan.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
}

/** Ein bereits gespeichertes PDF für die Liste „Zuletzt gespeichert“. */
data class SavedScan(
    val name: String,
    val uri: String,
    val location: String,
    val pages: Int,
    val savedAt: Long,
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
            )
        }
    }
}

class SettingsStore(private val context: Context) {
    private val keyTarget = stringPreferencesKey("target")
    private val keyTreeUri = stringPreferencesKey("tree_uri")
    private val keyTreeLabel = stringPreferencesKey("tree_label")
    private val keyRecent = stringPreferencesKey("recent")

    val target: Flow<Target> = context.dataStore.data.map { p ->
        val uri = p[keyTreeUri]
        if (p[keyTarget] == "folder" && !uri.isNullOrBlank()) {
            Target.Folder(uri, p[keyTreeLabel] ?: "Gewählter Ordner")
        } else {
            Target.Downloads
        }
    }

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
            }
        }
    }

    suspend fun addRecent(scan: SavedScan) {
        context.dataStore.edit { p ->
            val list = ScanListCodec.decode(p[keyRecent]).filterNot { it.uri == scan.uri }
            p[keyRecent] = ScanListCodec.encode(listOf(scan) + list)
        }
    }

    suspend fun removeRecent(uri: String) {
        context.dataStore.edit { p ->
            p[keyRecent] = ScanListCodec.encode(ScanListCodec.decode(p[keyRecent]).filterNot { it.uri == uri })
        }
    }
}
