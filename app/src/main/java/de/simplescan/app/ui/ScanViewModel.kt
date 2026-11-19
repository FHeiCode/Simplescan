package de.simplescan.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.simplescan.app.data.FileNames
import de.simplescan.app.data.PdfStorage
import de.simplescan.app.data.Presence
import de.simplescan.app.data.RecentPrune
import de.simplescan.app.data.WebDavPaths
import de.simplescan.app.data.SavedScan
import de.simplescan.app.data.SettingsStore
import de.simplescan.app.data.Target
import de.simplescan.app.data.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hinweis unten am Rand; mit [emailScan] bekommt er die Aktion „Per E-Mail“. */
data class UiMessage(val text: String, val emailScan: SavedScan? = null)

/** Ein fertiger Scan, der noch auf „Speichern“ wartet. */
data class PendingScan(val pdfUri: Uri, val pages: Int, val suggestedName: String)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val storage = PdfStorage(app)
    private val dav = WebDavClient(app)

    val target: StateFlow<Target> = settings.target
        .stateIn(viewModelScope, SharingStarted.Eagerly, Target.Downloads)
    val recent: StateFlow<List<SavedScan>> = settings.recent
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val defaultEmail: StateFlow<String> = settings.defaultEmail
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Speichert die festen Empfänger. Gibt eine Fehlermeldung zurück oder null, wenn alles gültig war. */
    fun saveDefaultEmail(input: String): String? {
        de.simplescan.app.data.EmailList.firstInvalid(input)?.let { return "„$it“ ist keine gültige E-Mail-Adresse." }
        viewModelScope.launch {
            settings.setDefaultEmail(input)
            show(if (input.isBlank()) "Keine feste Empfänger-Adresse mehr" else "Empfänger gespeichert")
        }
        return null
    }

    val lastWebDav: StateFlow<Target.WebDav?> = settings.lastWebDav
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _davBusy = MutableStateFlow(false)
    val davBusy: StateFlow<Boolean> = _davBusy.asStateFlow()

    private val _davError = MutableStateFlow<String?>(null)
    val davError: StateFlow<String?> = _davError.asStateFlow()

    private val _pending = MutableStateFlow<PendingScan?>(null)
    val pending: StateFlow<PendingScan?> = _pending.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _linkBusy = MutableStateFlow(false)
    val linkBusy: StateFlow<Boolean> = _linkBusy.asStateFlow()

    fun onScanned(pdfUri: Uri, pages: Int) {
        _pending.value = PendingScan(pdfUri, pages, FileNames.suggestion())
    }

    fun discardPending() {
        _pending.value = null
    }

    fun savePending(enteredName: String) {
        val scan = _pending.value ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try {
                val tgt = target.first()
                val name = FileNames.sanitize(enteredName, scan.suggestedName)
                val (savedName, uri) = when (tgt) {
                    is Target.WebDav -> {
                        val password = settings.webDavPassword()
                            ?: throw WebDavClient.DavException("Passwort fehlt. Bitte Nextcloud/WebDAV neu einrichten.")
                        val url = dav.upload(scan.pdfUri, name, tgt, password)
                        Uri.decode(url.substringAfterLast('/')) to url
                    }
                    else -> "$name.pdf" to storage.save(scan.pdfUri, name, tgt).toString()
                }
                val saved = SavedScan(
                    name = savedName,
                    uri = uri,
                    location = tgt.label,
                    pages = scan.pages,
                    savedAt = System.currentTimeMillis(),
                )
                settings.addRecent(saved)
                _pending.value = null
                _message.value = UiMessage("Gespeichert: $savedName (${tgt.label})", emailScan = saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(e.message ?: "Speichern fehlgeschlagen.")
            } finally {
                _saving.value = false
            }
        }
    }

    fun useDownloads() = viewModelScope.launch { settings.setTarget(Target.Downloads) }

    /** Ergebnis des Ordnerauswahl-Dialogs. Die Berechtigung bleibt auch nach einem Neustart erhalten. */
    fun useFolder(treeUri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            show("Für diesen Ordner gab es keine dauerhafte Freigabe. Bitte einen anderen wählen.")
            return
        }
        val label = FileNames.folderLabel(runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull())
        viewModelScope.launch {
            // Freigabe des vorher gewählten Ordners zurückgeben, damit sich keine Altlasten sammeln.
            val old = target.first()
            if (old is Target.Folder && old.treeUri != treeUri.toString()) {
                runCatching {
                    resolver.releasePersistableUriPermission(
                        Uri.parse(old.treeUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            settings.setTarget(Target.Folder(treeUri.toString(), label))
            show("Neuer Speicherort: $label")
        }
    }

    /**
     * Prüft die WebDAV-Zugangsdaten, legt den Ordner an und macht WebDAV zum Ziel.
     * Leeres Passwort = das gespeicherte weiterverwenden.
     */
    fun setupWebDav(target: Target.WebDav, password: String, onDone: () -> Unit) {
        if (_davBusy.value) return
        viewModelScope.launch {
            _davBusy.value = true
            _davError.value = null
            try {
                val pw = password.ifEmpty { settings.webDavPassword().orEmpty() }
                when {
                    target.server.isBlank() -> throw WebDavClient.DavException("Bitte die Server-Adresse eintragen.")
                    target.user.isBlank() -> throw WebDavClient.DavException("Bitte den Benutzernamen eintragen.")
                    pw.isEmpty() -> throw WebDavClient.DavException("Bitte das Passwort eintragen.")
                }
                dav.checkAndPrepare(target, pw)
                settings.setWebDav(target, pw)
                show("Neuer Speicherort: ${target.label}")
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _davError.value = e.message ?: "Verbindung fehlgeschlagen."
            } finally {
                _davBusy.value = false
            }
        }
    }

    fun clearDavError() {
        _davError.value = null
    }

    private var pruneJob: Job? = null

    /**
     * Entfernt Einträge, deren Datei gelöscht wurde – lokal (Download/eigener Ordner) und auf dem Server.
     * Läuft beim Öffnen der App und jedes Mal, wenn sie wieder in den Vordergrund kommt.
     */
    fun pruneRecent() {
        if (pruneJob?.isActive == true) return
        pruneJob = viewModelScope.launch {
            try {
                val entries = settings.recent.first()
                if (entries.isEmpty()) return@launch
                val davTarget = settings.lastWebDav.first()
                val davBase = davTarget?.let { WebDavPaths.baseUrl(it.kind, it.server, it.user) }
                val password = if (davTarget != null && entries.any { isRemote(it.uri) }) settings.webDavPassword() else null

                val status = entries.associate { e ->
                    e.uri to when {
                        !isRemote(e.uri) -> localPresence(e.uri)
                        // Nur mit den gespeicherten Zugangsdaten prüfbar; Dateien auf anderen Servern bleiben stehen.
                        davTarget != null && password != null && davBase != null && e.uri.startsWith(davBase) ->
                            dav.presence(e.uri, davTarget, password)
                        else -> Presence.UNKNOWN
                    }
                }
                settings.removeRecent(RecentPrune.toRemove(status))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Aufräumen ist Nebensache: bei Fehlern einfach Liste lassen, wie sie ist.
            }
        }
    }

    private fun isRemote(uri: String) = uri.startsWith("http://") || uri.startsWith("https://")

    private suspend fun localPresence(uri: String): Presence = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                ?.use { Presence.PRESENT } ?: Presence.GONE
        } catch (e: java.io.FileNotFoundException) {
            Presence.GONE
        } catch (e: IllegalArgumentException) {
            Presence.GONE
        } catch (e: SecurityException) {
            // Kein Zugriff mehr (z. B. Ordnerfreigabe entzogen): für uns nicht mehr erreichbar.
            Presence.GONE
        } catch (e: Exception) {
            Presence.UNKNOWN
        }
    }

    /**
     * Links zu hochgeladenen Scans. Bei Nextcloud ein öffentlicher Link (ohne Anmeldung zu öffnen),
     * einmal erstellt und gemerkt. Bei anderen WebDAV-Servern die direkte Adresse (Empfänger braucht Zugang).
     * [onReady] bekommt je Datei-Adresse den Link und ob man sich zum Öffnen anmelden muss.
     * Klappt ein Link nicht, gibt es eine Meldung und [onReady] wird nicht aufgerufen.
     */
    fun linksFor(scans: List<SavedScan>, onReady: (Map<String, Pair<String, Boolean>>) -> Unit) {
        val remote = scans.filter { isRemote(it.uri) }
        if (remote.isEmpty()) { onReady(emptyMap()); return }
        if (_linkBusy.value) return
        viewModelScope.launch {
            _linkBusy.value = true
            var current: SavedScan? = null
            try {
                val result = LinkedHashMap<String, Pair<String, Boolean>>()
                var announced = false
                for (scan in remote) {
                    current = scan
                    val cached = recent.value.firstOrNull { it.uri == scan.uri }?.shareUrl ?: scan.shareUrl
                    result[scan.uri] = when {
                        cached != null -> cached to false
                        WebDavPaths.nextcloudRoot(scan.uri) == null -> scan.uri to true
                        else -> {
                            if (!announced) { show(if (remote.size == 1) "Link wird erstellt …" else "Links werden erstellt …"); announced = true }
                            createLink(scan) to false
                        }
                    }
                }
                if (announced) messageShown()
                onReady(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val name = current?.name?.let { " für „$it“" } ?: ""
                show("Link$name konnte nicht erstellt werden: ${e.message ?: "unbekannter Fehler"}")
            } finally {
                _linkBusy.value = false
            }
        }
    }

    private suspend fun createLink(scan: SavedScan): String {
        val davTarget = settings.lastWebDav.first()
        val base = davTarget?.let { WebDavPaths.baseUrl(it.kind, it.server, it.user) }
        val password = settings.webDavPassword()
        if (davTarget == null || base == null || password == null || !scan.uri.startsWith(base)) {
            throw WebDavClient.DavException("Für diesen Server sind keine Zugangsdaten mehr gespeichert.")
        }
        val link = dav.createPublicLink(scan.uri, davTarget, password)
        settings.setShareUrl(scan.uri, link)
        return link
    }

    fun forget(scan: SavedScan) = viewModelScope.launch { settings.removeRecent(scan.uri) }

    fun show(text: String) {
        _message.value = UiMessage(text)
    }

    fun messageShown() = _message.update { null }
}
