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

    val target: StateFlow<Target> = settings.target
        .stateIn(viewModelScope, SharingStarted.Eagerly, Target.Downloads)
    val recent: StateFlow<List<SavedScan>> = settings.recent
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                val uri = storage.save(scan.pdfUri, name, tgt)
                settings.addRecent(
                    SavedScan(
                        name = "$name.pdf",
                        uri = uri.toString(),
                        location = tgt.label,
                        pages = scan.pages,
                        savedAt = System.currentTimeMillis(),
                    )
                )
                _pending.value = null
                show("Gespeichert: $name.pdf (${tgt.label})")
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

    fun forget(scan: SavedScan) = viewModelScope.launch { settings.removeRecent(scan.uri) }

    fun show(text: String) {
        _message.value = text
    }

    fun messageShown() = _message.update { null }
}
