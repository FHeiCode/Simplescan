package de.simplescan.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Lädt PDFs per WebDAV hoch (Nextcloud, ownCloud, andere WebDAV-Server). */
class WebDavClient(private val context: Context) {

    class DavException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val userAgent: String by lazy {
        val v = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        "Simplescan/${v ?: "?"} (Android)"
    }

    private val propfindBody =
        """<?xml version="1.0" encoding="UTF-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody("application/xml; charset=utf-8".toMediaType())

    /**
     * Prüft Zugang und Ordner. Fehlt der Ordner, wird er (samt Unterordnern) angelegt.
     */
    suspend fun checkAndPrepare(target: Target.WebDav, password: String) = withContext(Dispatchers.IO) {
        val base = WebDavPaths.baseUrl(target.kind, target.server, target.user)
        // Erst die Basis: hier zeigt sich, ob Adresse und Zugangsdaten stimmen.
        call(request(base, target, password).method("PROPFIND", propfindBody).header("Depth", "0")).use { r ->
            when {
                r.code == 207 || r.isSuccessful -> Unit
                r.code == 401 || r.code == 403 -> throw DavException("Benutzername oder Passwort falsch.")
                r.code == 404 -> throw DavException(
                    if (target.kind == DavKind.NEXTCLOUD) {
                        "Nicht gefunden. Stimmt die Adresse? Bei Nextcloud muss der Benutzername der in der WebDAV-Adresse sein (Dateien → Einstellungen → WebDAV)."
                    } else {
                        "Unter dieser Adresse gibt es keinen WebDAV-Ordner."
                    }
                )
                r.code == 405 -> throw DavException("Der Server spricht unter dieser Adresse kein WebDAV.")
                else -> throw DavException("Der Server antwortet mit Fehler ${r.code}.")
            }
        }
        // Dann den Zielordner Stück für Stück anlegen, falls nötig.
        var path = ""
        for (segment in WebDavPaths.folderSegments(target.folder)) {
            path = if (path.isEmpty()) segment else "$path/$segment"
            val url = WebDavPaths.folderUrl(base, path)
            val exists = call(request(url, target, password).method("PROPFIND", propfindBody).header("Depth", "0")).use {
                it.code == 207 || it.isSuccessful
            }
            if (!exists) {
                call(request(url, target, password).method("MKCOL", null)).use { r ->
                    // 405 = gibt es schon
                    if (!r.isSuccessful && r.code != 405) {
                        throw DavException("Der Ordner „$path“ konnte nicht angelegt werden (Fehler ${r.code}).")
                    }
                }
            }
        }
    }

    /** Lädt das PDF hoch und gibt die Adresse der Datei zurück. Vorhandene Dateien werden nie überschrieben. */
    suspend fun upload(source: Uri, baseName: String, target: Target.WebDav, password: String): String =
        withContext(Dispatchers.IO) {
            val base = WebDavPaths.baseUrl(target.kind, target.server, target.user)
            var url: String? = null
            for (attempt in 0..99) {
                val candidate = WebDavPaths.fileUrl(base, target.folder, WebDavPaths.candidateName(baseName, attempt))
                val code = call(request(candidate, target, password).head()).use { it.code }
                if (code == 404) { url = candidate; break }
                if (code == 401 || code == 403) throw DavException("Benutzername oder Passwort falsch.")
                if (code !in 200..299) throw DavException("Der Server antwortet mit Fehler $code.")
            }
            val dest = url ?: throw DavException("Kein freier Dateiname gefunden.")

            call(request(dest, target, password).put(PdfBody(source))).use { r ->
                when {
                    r.isSuccessful -> dest
                    r.code == 401 || r.code == 403 -> throw DavException("Benutzername oder Passwort falsch.")
                    r.code == 404 || r.code == 409 -> throw DavException("Der Zielordner fehlt. Bitte den Speicherort neu einrichten.")
                    r.code == 507 -> throw DavException("Auf dem Server ist kein Platz mehr frei.")
                    else -> throw DavException("Hochladen fehlgeschlagen (Fehler ${r.code}).")
                }
            }
        }

    private fun request(url: String, target: Target.WebDav, password: String) = Request.Builder()
        .url(url)
        .header("Authorization", Credentials.basic(target.user, password, Charsets.UTF_8))
        .header("User-Agent", userAgent)

    private fun call(builder: Request.Builder): Response {
        val req = try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            throw DavException("Die Server-Adresse ist ungültig.", e)
        }
        return try {
            http.newCall(req).execute()
        } catch (e: UnknownHostException) {
            throw DavException("Server nicht gefunden. Adresse und Internetverbindung prüfen.", e)
        } catch (e: SSLException) {
            throw DavException("Sichere Verbindung fehlgeschlagen (Zertifikat des Servers wird nicht anerkannt).", e)
        } catch (e: IOException) {
            throw DavException("Keine Verbindung zum Server: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Liest das PDF direkt aus dem Scanner-Ergebnis, ohne es ganz in den Speicher zu laden. */
    private inner class PdfBody(private val uri: Uri) : RequestBody() {
        override fun contentType() = "application/pdf".toMediaType()

        override fun contentLength(): Long =
            runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
                .getOrNull()?.takeIf { it >= 0 } ?: -1L

        override fun writeTo(sink: BufferedSink) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Der Scan konnte nicht gelesen werden.")
            input.source().use { sink.writeAll(it) }
        }
    }
}
