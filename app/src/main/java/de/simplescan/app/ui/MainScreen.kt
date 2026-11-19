package de.simplescan.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.simplescan.app.data.DavKind
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
    // Gelöschte Dateien aus der Liste nehmen, sobald die App (wieder) sichtbar ist.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.pruneRecent() }

    LaunchedEffect(message) {
        message?.let { m ->
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = if (m.emailScan != null) "Per E-Mail" else null,
                withDismissAction = m.emailScan != null,
                duration = if (m.emailScan != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            vm.messageShown()
            if (result == SnackbarResult.ActionPerformed) m.emailScan?.let { sendEmail(context, it, vm) }
        }
    }

    var showTargetDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    val lastWebDav by vm.lastWebDav.collectAsStateWithLifecycle()
    val davBusy by vm.davBusy.collectAsStateWithLifecycle()
    val davError by vm.davError.collectAsStateWithLifecycle()

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
                        onEmail = { sendEmail(context, scan, vm) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pending?.let { scan ->
        SaveDialog(
            scan = scan,
            targetLabel = target.label,
            saving = saving,
            onSave = vm::savePending,
            onDiscard = vm::discardPending,
            onChangeTarget = { showTargetDialog = true },
        )
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
            onWebDav = { showTargetDialog = false; vm.clearDavError(); showWebDavDialog = true },
            onDismiss = { showTargetDialog = false },
        )
    }

    if (showWebDavDialog) {
        WebDavDialog(
            initial = (target as? Target.WebDav) ?: lastWebDav,
            busy = davBusy,
            error = davError,
            onSave = { t, pw -> vm.setupWebDav(t, pw) { showWebDavDialog = false } },
            onDismiss = { if (!davBusy) showWebDavDialog = false },
        )
    }

}

@Composable
private fun RecentRow(scan: SavedScan, onOpen: () -> Unit, onShare: () -> Unit, onEmail: () -> Unit) {
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
            Row {
                IconButton(onClick = onEmail) {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = if (isRemote(scan)) "Link per E-Mail senden" else "Per E-Mail senden",
                    )
                }
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Teilen") }
            }
        },
    )
}

@Composable
private fun TargetDialog(
    current: Target,
    onDownloads: () -> Unit,
    onPickFolder: () -> Unit,
    onWebDav: () -> Unit,
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
                OptionRow(
                    selected = current is Target.WebDav,
                    title = "Nextcloud / WebDAV …",
                    subtitle = (current as? Target.WebDav)?.label ?: "Direkt auf deinen Server hochladen",
                    onClick = onWebDav,
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
    onChangeTarget: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$pages · Ziel: $targetLabel",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChangeTarget, enabled = !saving) { Text("Ändern") }
                }
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
private fun isRemote(scan: SavedScan) = scan.uri.startsWith("http://") || scan.uri.startsWith("https://")

private fun exists(context: Context, scan: SavedScan, vm: ScanViewModel): Boolean {
    if (isRemote(scan)) return true // liegt auf dem Server, hier nicht prüfbar
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
    if (isRemote(scan)) {
        // Auf dem Server: im Browser öffnen (der Server fragt dort nach der Anmeldung).
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scan.uri)))
        } catch (e: ActivityNotFoundException) {
            vm.show("Kein Browser gefunden.")
        }
        return
    }
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
    if (isRemote(scan)) {
        // Auf dem Server liegt nur der Link vor; die Datei selbst ist nicht auf dem Handy.
        vm.linkFor(scan) { link, _ ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
            context.startActivity(Intent.createChooser(send, scan.name))
        }
        return
    }
    if (!exists(context, scan, vm)) return
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(scan.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, scan.name))
}

@Composable
private fun WebDavDialog(
    initial: Target.WebDav?,
    busy: Boolean,
    error: String?,
    onSave: (Target.WebDav, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: DavKind.NEXTCLOUD) }
    var server by rememberSaveable { mutableStateOf(initial?.server ?: "") }
    var user by rememberSaveable { mutableStateOf(initial?.user ?: "") }
    var password by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf(initial?.folder ?: "Scans") }
    val hasStoredPassword = initial != null

    fun submit() = onSave(Target.WebDav(kind, server.trim(), user.trim(), folder.trim()), password)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nextcloud / WebDAV") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OptionRow(kind == DavKind.NEXTCLOUD, "Nextcloud", "Normale Adresse genügt, z. B. cloud.example.de") {
                    kind = DavKind.NEXTCLOUD
                }
                OptionRow(kind == DavKind.WEBDAV, "Anderer WebDAV-Server", "Vollständige WebDAV-Adresse") {
                    kind = DavKind.WEBDAV
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text(if (kind == DavKind.NEXTCLOUD) "Server-Adresse" else "WebDAV-Adresse") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Benutzername") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (kind == DavKind.NEXTCLOUD) "App-Passwort" else "Passwort") },
                    placeholder = { if (hasStoredPassword) Text("unverändert lassen") },
                    singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = folder, onValueChange = { folder = it },
                    label = { Text("Ordner auf dem Server") },
                    supportingText = { Text("Wird angelegt, falls er fehlt. Leer = oberste Ebene.") },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy) submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == DavKind.NEXTCLOUD) {
                    Text(
                        "App-Passwort anlegen: in Nextcloud unter Persönliche Einstellungen → Sicherheit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Prüfen und speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } },
    )
}

/**
 * Direkt per E-Mail senden: lokal gespeicherte PDFs als Anhang, hochgeladene als Link.
 * Der „mailto:“-Selektor sorgt dafür, dass nur E-Mail-Apps angeboten werden.
 */
private fun sendEmail(context: Context, scan: SavedScan, vm: ScanViewModel) {
    val mailOnly = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    if (isRemote(scan)) {
        vm.linkFor(scan) { link, needsLogin ->
            val hint = if (needsLogin) "\n\n(Zum Öffnen sind Zugangsdaten für den Server nötig.)" else ""
            val body = "Hier ist der Scan „${scan.name}“:\n$link$hint"
            // Betreff und Text stehen zusätzlich in der mailto-Adresse, weil manche Mail-Apps die Extras ignorieren.
            val mailto = Uri.parse("mailto:?subject=" + Uri.encode(scan.name) + "&body=" + Uri.encode(body))
            val mail = Intent(Intent.ACTION_SENDTO, mailto)
                .putExtra(Intent.EXTRA_SUBJECT, scan.name)
                .putExtra(Intent.EXTRA_TEXT, body)
            startMail(context, mail, vm)
        }
        return
    }
    if (!exists(context, scan, vm)) return
    val uri = Uri.parse(scan.uri)
    val mail = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, scan.name)
        .putExtra(Intent.EXTRA_TEXT, "Anbei der Scan „${scan.name}“.")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    mail.clipData = ClipData.newRawUri(scan.name, uri)
    mail.selector = mailOnly
    startMail(context, mail, vm)
}

private fun startMail(context: Context, intent: Intent, vm: ScanViewModel) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        vm.show("Keine E-Mail-App gefunden.")
    }
}
