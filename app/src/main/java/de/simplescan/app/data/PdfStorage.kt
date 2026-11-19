package de.simplescan.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Kopiert das vom Scanner erzeugte PDF an den gewählten Speicherort. */
class PdfStorage(private val context: Context) {

    class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /** Gibt die URI der gespeicherten Datei zurück. */
    suspend fun save(source: Uri, baseName: String, target: Target): Uri = withContext(Dispatchers.IO) {
        when (target) {
            Target.Downloads -> saveToDownloads(source, baseName)
            is Target.Folder -> saveToFolder(source, baseName, target)
        }
    }

    private fun saveToDownloads(source: Uri, baseName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$baseName.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw StorageException("Im Download-Ordner konnte keine Datei angelegt werden.")
        try {
            copy(source, dest)
            resolver.update(dest, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return dest
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            throw e
        }
    }

    private fun saveToFolder(source: Uri, baseName: String, target: Target.Folder): Uri {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(target.treeUri))
        if (tree == null || !tree.exists() || !tree.canWrite()) {
            throw StorageException("Der Ordner „${target.label}“ ist nicht mehr erreichbar. Bitte den Speicherort neu wählen.")
        }
        // Android hängt bei gleichem Namen selbst „ (1)“ an und ergänzt „.pdf“.
        val file = tree.createFile("application/pdf", baseName)
            ?: throw StorageException("Im Ordner „${target.label}“ konnte keine Datei angelegt werden.")
        try {
            copy(source, file.uri)
            return file.uri
        } catch (e: Exception) {
            runCatching { file.delete() }
            throw e
        }
    }

    private fun copy(source: Uri, dest: Uri) {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(source)
            ?: throw StorageException("Der Scan konnte nicht gelesen werden.")
        input.use { inp ->
            val out = resolver.openOutputStream(dest)
                ?: throw StorageException("Die Zieldatei konnte nicht geschrieben werden.")
            out.use { inp.copyTo(it) }
        }
    }
}
