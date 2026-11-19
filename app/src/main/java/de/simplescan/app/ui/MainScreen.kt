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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import de.simplescan.app.R
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.material3.TopAppBarDefaults
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

// Farben aus dem App-Icon, damit Startseite und Homescreen-Symbol zusammenpassen.
private val StageGreen = Color(0xFF1F6F5C)
private val StageMint = Color(0xFFB9E4D6)

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
            if (result == SnackbarResult.ActionPerformed) m.emailScan?.let { sendScans(context, listOf(it), vm, email = true) }
        }
    }

    var showTargetDialog by remember { mutableStateOf(false) }

    // Mehrfachauswahl: Adressen der ausgewählten Einträge. Leer = normale Ansicht.
    var selection by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = selection.isNotEmpty()
    fun toggle(scan: SavedScan) {
        selection = if (scan.uri in selection) selection - scan.uri else selection + scan.uri
    }
    // Gelöschte Einträge fallen auch aus der Auswahl.
    LaunchedEffect(recent) {
        val uris = recent.map { it.uri }.toSet()
        if (selection.any { it !in uris }) selection = selection.filter { it in uris }
    }
    BackHandler(enabled = selecting) { selection = emptyList() }
    fun selectedScans() = recent.filter { it.uri in selection }
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
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selection = emptyList() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Auswahl beenden")
                        }
                    },
                    title = { Text("${selection.size} ausgewählt") },
                    actions = {
                        IconButton(onClick = { selection = recent.map { it.uri } }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Alle auswählen")
                        }
                        IconButton(onClick = {
                            sendScans(context, selectedScans(), vm, email = true)
                            selection = emptyList()
                        }) { Icon(Icons.Outlined.Email, contentDescription = "Ausgewählte per E-Mail senden") }
                        IconButton(onClick = {
                            sendScans(context, selectedScans(), vm, email = false)
                            selection = emptyList()
                        }) { Icon(Icons.Outlined.Share, contentDescription = "Ausgewählte teilen") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = StageGreen,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            } else {
                // Fester Kopf (scrollt nicht mit): Logo, Schriftzug und Unterzeile auf Grün.
                Stage()
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(
                onClick = { startScan(context, scanLauncher, vm) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
                if (recent.size > 1 && !selecting) {
                    Text(
                        "Lange drücken, um mehrere auszuwählen und zusammen zu verschicken.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recent.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Noch nichts gescannt.\nTippe auf „Scannen“, halte die Kamera über ein Blatt –\nRänder werden automatisch erkannt.\n\nMehrere Seiten? Nach dem ersten Foto einfach weitere\nSeiten hinzufügen – alle landen in einem PDF.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(recent, key = { it.uri }) { scan ->
                    RecentRow(
                        scan = scan,
                        selecting = selecting,
                        selected = scan.uri in selection,
                        onOpen = { if (selecting) toggle(scan) else openPdf(context, scan, vm) },
                        onLongPress = { toggle(scan) },
                        onShare = { sendScans(context, listOf(scan), vm, email = false) },
                        onEmail = { sendScans(context, listOf(scan), vm, email = true) },
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

/** Startseiten-Kopf: App-Logo, Schriftzug und Unterzeile auf grüner Fläche. */
@Composable
private fun Stage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(StageGreen)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 4.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Das Vordergrund-Motiv des App-Icons (Blatt mit Scan-Ecken), ohne Hintergrund.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            "Simplescan",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(2.dp))
        Text("Scannen. Speichern. Senden.", color = StageMint, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    scan: SavedScan,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
    onEmail: () -> Unit,
) {
    val date = remember(scan.savedAt) {
        dateFormat.format(Instant.ofEpochMilli(scan.savedAt).atZone(ZoneId.systemDefault()))
    }
    val pages = if (scan.pages == 1) "1 Seite" else "${scan.pages} Seiten"
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            if (selecting) Checkbox(checked = selected, onCheckedChange = null)
            else Icon(Icons.Outlined.Description, contentDescription = null)
        },
        headlineContent = { Text(scan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("$date · $pages · ${scan.location}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (selecting) null else {
            { Row {
                IconButton(onClick = onEmail) {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = if (isRemote(scan)) "Link per E-Mail senden" else "Per E-Mail senden",
                    )
                }
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Teilen") }
            } }
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
internal fun isRemote(scan: SavedScan) = scan.uri.startsWith("http://") || scan.uri.startsWith("https://")

internal fun exists(context: Context, scan: SavedScan, vm: ScanViewModel): Boolean {
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
