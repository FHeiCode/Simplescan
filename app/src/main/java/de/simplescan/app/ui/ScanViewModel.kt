package de.simplescan.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.simplescan.app.data.FileNames
import de.simplescan.app.data.PdfStorage
import de.simplescan.app.data.SavedScan
import de.simplescan.app.data.SettingsStore
import de.simplescan.app.data.Target
import de.simplescan.app.data.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
                settings.addRecent(
                    SavedScan(
                        name = savedName,
                        uri = uri,
                        location = tgt.label,
                        pages = scan.pages,
                        savedAt = System.currentTimeMillis(),
                    )
                )
                _pending.value = null
                show("Gespeichert: $savedName (${tgt.label})")
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

    fun forget(scan: SavedScan) = viewModelScope.launch { settings.removeRecent(scan.uri) }

    fun show(text: String) {
        _message.value = text
    }

    fun messageShown() = _message.update { null }
}
