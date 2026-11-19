package de.simplescan.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.simplescan.app.data.SavedScan
import de.simplescan.app.data.Target
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    val target by vm.target.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    var showTargetDialog by remember { mutableStateOf(false) }

    // Ergebnis des Scanners: fertiges PDF im App-Cache, das wir danach an den Speicherort kopieren.
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pdf
            if (pdf != null) vm.onScanned(pdf.uri, pdf.pageCount) else vm.show("Der Scanner hat kein PDF geliefert.")
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.useFolder(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Simplescan") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { startScan(context, scanLauncher, vm) },
                icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                text = { Text("Scannen") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    onClick = { showTargetDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Speicherort", style = MaterialTheme.typography.labelMedium)
                            Text(target.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { showTargetDialog = true }) { Text("Ändern") }
                    }
                }
            }

            item {
                Text(
                    "Zuletzt gespeichert",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }

            if (recent.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Noch nichts gescannt.\nTippe auf „Scannen“, halte die Kamera über ein Blatt –\nRänder werden automatisch erkannt.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(recent, key = { it.uri }) { scan ->
                    RecentRow(
                        scan = scan,
                        onOpen = { openPdf(context, scan, vm) },
                        onShare = { sharePdf(context, scan, vm) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showTargetDialog) {
        TargetDialog(
            current = target,
            onDownloads = { vm.useDownloads(); showTargetDialog = false },
            onPickFolder = {
                showTargetDialog = false
                try {
                    folderLauncher.launch(null)
                } catch (e: ActivityNotFoundException) {
                    vm.show("Auf diesem Gerät gibt es keinen Ordnerauswahl-Dialog.")
                }
            },
            onDismiss = { showTargetDialog = false },
        )
    }

    pending?.let { scan ->
        SaveDialog(
            scan = scan,
            targetLabel = target.label,
            saving = saving,
            onSave = vm::savePending,
            onDiscard = vm::discardPending,
        )
    }
}

@Composable
private fun RecentRow(scan: SavedScan, onOpen: () -> Unit, onShare: () -> Unit) {
    val date = remember(scan.savedAt) {
        dateFormat.format(Instant.ofEpochMilli(scan.savedAt).atZone(ZoneId.systemDefault()))
    }
    val pages = if (scan.pages == 1) "1 Seite" else "${scan.pages} Seiten"
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
        headlineContent = { Text(scan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("$date · $pages · ${scan.location}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Teilen") }
        },
    )
}

@Composable
private fun TargetDialog(
    current: Target,
    onDownloads: () -> Unit,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDFs speichern in") },
        text = {
            Column {
                OptionRow(selected = current is Target.Downloads, title = "Download-Ordner", subtitle = null, onClick = onDownloads)
                OptionRow(
                    selected = current is Target.Folder,
                    title = "Eigener Ordner …",
                    subtitle = (current as? Target.Folder)?.label ?: "Ordner auf dem Telefon wählen",
                    onClick = onPickFolder,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun OptionRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SaveDialog(
    scan: PendingScan,
    targetLabel: String,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by rememberSaveable(scan.pdfUri.toString()) { mutableStateOf(scan.suggestedName) }
    val pages = if (scan.pages == 1) "1 Seite" else "${scan.pages} Seiten"
    AlertDialog(
        onDismissRequest = { if (!saving) onDiscard() },
        title = { Text("PDF speichern") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Dateiname") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!saving) onSave(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("$pages · Ziel: $targetLabel", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDiscard, enabled = !saving) { Text("Verwerfen") } },
    )
}

private fun startScan(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>, vm: ScanViewModel) {
    val activity = context as? Activity ?: return
    val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // Erkennen, Zuschneiden, Filter
        .setGalleryImportAllowed(true)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .build()
    GmsDocumentScanning.getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
        .addOnFailureListener { e ->
            vm.show("Scanner startet nicht (Google-Play-Dienste nötig, beim ersten Mal Internet): ${e.localizedMessage ?: e}")
        }
}

/** Prüft, ob die Datei noch da ist; sonst verschwindet sie aus der Liste. */
private fun exists(context: Context, scan: SavedScan, vm: ScanViewModel): Boolean {
    val ok = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(scan.uri), "r")?.use { true } ?: false
    }.getOrDefault(false)
    if (!ok) {
        vm.forget(scan)
        vm.show("„${scan.name}“ gibt es nicht mehr und wurde aus der Liste entfernt.")
    }
    return ok
}

private fun openPdf(context: Context, scan: SavedScan, vm: ScanViewModel) {
    if (!exists(context, scan, vm)) return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(scan.uri), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        vm.show("Keine App zum Anzeigen von PDFs gefunden.")
    }
}

private fun sharePdf(context: Context, scan: SavedScan, vm: ScanViewModel) {
    if (!exists(context, scan, vm)) return
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(scan.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, scan.name))
}
