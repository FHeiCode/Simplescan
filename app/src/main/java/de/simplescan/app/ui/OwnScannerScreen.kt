package de.simplescan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.TimeUnit
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.simplescan.app.data.PdfBuilder
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.rememberUpdatedState
import de.simplescan.app.data.AutoCaptureTracker
import de.simplescan.app.data.PageFilter
import de.simplescan.app.data.EditGeometry
import de.simplescan.app.data.PageDetector
import de.simplescan.app.data.PdfPages
import de.simplescan.app.data.Pt
import de.simplescan.app.data.QuadMath
import de.simplescan.app.data.QuadStabilizer
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File

private val ScanGreen = Color(0xFF1F6F5C)
private val ScanMint = Color(0xFFB9E4D6)

/**
 * Scanner: eigene Kamera-Ansicht im App-Grün, Auslöser, Blitz, mehrere Seiten.
 * Schritt 2: Live-Rahmen um die erkannte Seite; nach dem Foto wird sie ausgeschnitten und entzerrt.
 * Schritt 3: Seitenübersicht (drehen, löschen, umsortieren) und Ecken von Hand anpassen.
 */
@Composable
fun OwnScannerScreen(onDone: (List<File>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val session = remember { ScanSession() }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("camera") } // camera | overview | edit
    var editing by remember { mutableStateOf<ScanPage?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    fun close() {
        session.discard()
        onClose()
    }

    // OpenCV laden (Seitenerkennung, Entzerren, Filter). Klappt das nicht, gibt es nur ganze Fotos.
    val openCvReady = remember { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    var autoOn by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }

    /** Seite mit neuen Ecken, Drehung oder neuem Filter neu berechnen. */
    fun rerender(page: ScanPage, quad: List<Pt>?, rotation: Int, filter: PageFilter = page.filter) {
        busyId = page.id
        scope.launch {
            try {
                session.replace(PageRenderer.render(context, page.id, page.photo, quad, rotation, filter, page.focalRatio))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Simplescan", "Seite neu berechnen", e)
            } finally {
                busyId = null
            }
        }
    }

    /** Filter für alle Seiten setzen und nacheinander neu berechnen. */
    fun filterAll(filter: PageFilter) {
        session.filter = filter
        scope.launch {
            for (page in session.pages.toList()) {
                if (page.filter == filter) continue
                busyId = page.id
                try {
                    session.replace(PageRenderer.render(context, page.id, page.photo, page.quad, page.rotation, filter, page.focalRatio))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Simplescan", "Filter", e)
                }
            }
            busyId = null
        }
    }

    // Fotos aus der Galerie: Seite suchen, zuschneiden, Filter – dann zur Übersicht.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            for (uri in uris) {
                try {
                    val page = withContext(Dispatchers.Default) {
                        val bmp = loadGalleryImage(context, uri)
                        val quad = if (!openCvReady) null else
                            runCatching { PageDetector.detect(bmp) }.getOrNull()
                        val photo = PageRenderer.savePhoto(context, bmp)
                        bmp.recycle()
                        // Galerie: Brennweite unbekannt → typischer Handywert
                        PageRenderer.render(context, System.nanoTime(), photo, quad, 0, session.filter, null)
                    }
                    session.pages.add(page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w("Simplescan", "Galerie", e)
                }
            }
            importing = false
            mode = "overview"
        }
    }

    when (mode) {
        "overview" -> {
            PagesOverview(
                session = session,
                busyId = busyId,
                onEdit = { editing = it; mode = "edit" },
                // Tipp auf eine Seite: bei „Original“ wie bisher Ecken anpassen, sonst große Ansicht im gewählten Filter.
                onOpen = { editing = it; mode = if (session.filter == PageFilter.ORIGINAL) "edit" else "preview" },
                onRotate = { rerender(it, it.quad, EditGeometry.rotateRight(it.rotation)) },
                onFilter = { rerender(it, it.quad, it.rotation, it.filter.next()) },
                onFilterAll = { filterAll(it) },
                onAddMore = { mode = "camera" },
                onFinish = { onDone(session.finish()) },
                onClose = { close() },
            )
            return
        }
        "preview" -> {
            val page = editing?.let { e -> session.pages.firstOrNull { it.id == e.id } }
            if (page != null) {
                PagePreview(
                    page = page,
                    number = session.pages.indexOf(page) + 1,
                    filter = session.filter,
                    onEditCorners = { editing = page; mode = "edit" },
                    onBack = { mode = "overview" },
                )
                return
            }
            mode = "overview"
        }
        "edit" -> {
            val page = editing
            if (page != null) {
                CornerEditor(
                    page = page,
                    openCvReady = openCvReady,
                    onApply = { quad -> rerender(page, quad, page.rotation); mode = "overview" },
                    onCancel = { mode = "overview" },
                )
                return
            }
            mode = "overview"
        }
    }
    BackHandler { if (session.pages.isEmpty()) close() else mode = "overview" }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Kopf
        Row(
            Modifier.fillMaxWidth().background(ScanGreen).windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { close() }) { Icon(Icons.Outlined.Close, "Abbrechen", tint = Color.White) }
            Text("Scanner", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp, modifier = Modifier.weight(1f))
            // Automatisch auslösen an/aus
            TextButton(onClick = { autoOn = !autoOn }) {
                Text(if (autoOn) "Auto: an" else "Auto: aus", color = if (autoOn) ScanMint else Color.White, fontSize = 14.sp)
            }
            IconButton(
                onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !importing,
            ) {
                if (importing) CircularProgressIndicator(Modifier.size(22.dp), color = ScanMint, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.PhotoLibrary, "Aus der Galerie", tint = Color.White)
            }
        }

        if (!hasPermission) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Für den eigenen Scanner braucht Simplescan Zugriff auf die Kamera.",
                    color = Color.White, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = ScanMint, contentColor = ScanGreen),
                ) { Text("Kamera erlauben") }
            }
        } else {
            CameraArea(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                session = session,
                openCvReady = openCvReady,
                autoOn = autoOn,
                onShowPages = { mode = "overview" },
            )
        }
    }
}

@Composable
private fun CameraArea(
    modifier: Modifier, session: ScanSession, openCvReady: Boolean, autoOn: Boolean, onShowPages: () -> Unit,
) {
    val pages = session.pages
    val autoOnNow by rememberUpdatedState(autoOn)
    val tracker = remember { AutoCaptureTracker() }
    var steadying by remember { mutableStateOf(false) }
    var autoFire by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val ratio43 = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    // Höchste verfügbare Auflösung im Fotoformat 4:3 (sonst wählt CameraX evtl. eine kleinere).
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
            .build()
    }
    // Kleines Vorschaubild für die Live-Erkennung (gleiches 4:3-Format wie Vorschau und Foto).
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
            )
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Brennweite der Kamera (für das echte Seitenverhältnis schräg fotografierter Seiten)
    var focalRatio by remember { mutableStateOf<Double?>(null) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Live-Ecken in 0..1 des (gedrehten) Analysebilds, dazu dessen Seitenverhältnis.
    var liveQuad by remember { mutableStateOf<List<Pt>?>(null) }
    var liveAspect by remember { mutableStateOf(3f / 4f) }
    val lastThumb = pages.lastOrNull()?.thumb
    var lastDetected by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        try {
            val provider = cameraProvider(context)
            val preview = Preview.Builder().setResolutionSelector(ratio43).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            if (openCvReady) {
                val stabilizer = QuadStabilizer()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val plane = image.planes[0]
                        val gray = PageDetector.grayFromY(
                            plane.buffer, image.width, image.height, plane.rowStride, image.imageInfo.rotationDegrees,
                        )
                        val w = gray.cols().toDouble()
                        val h = gray.rows().toDouble()
                        val quad = PageDetector.detect(gray)?.map { Pt(it.x / w, it.y / h) }
                        gray.release()
                        val show = stabilizer.update(quad)
                        val fire = tracker.update(show, System.currentTimeMillis())
                        val steady = tracker.steadying
                        val aspect = (w / h).toFloat()
                        ContextCompat.getMainExecutor(context).execute {
                            liveQuad = show; liveAspect = aspect; steadying = steady
                            if (fire && autoOnNow) autoFire++
                        }
                    } catch (e: Exception) {
                        Log.w("Simplescan", "Erkennung", e)
                    } finally {
                        image.close()
                    }
                }
            }
            provider.unbindAll()
            camera = if (openCvReady) {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            } else {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }
            focalRatio = camera?.let { cameraFocalRatio(it) }
            Log.i("Simplescan", "Brennweite je Pixel: $focalRatio")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Simplescan", "Kamera", e)
            error = "Kamera konnte nicht gestartet werden: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
        }
    }
    LaunchedEffect(flash) {
        imageCapture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    /** Scharfstellen auf einen Punkt der Vorschau (Bildmitte, wenn nichts angegeben), dann [then]. */
    fun focus(x: Float? = null, y: Float? = null, then: (() -> Unit)? = null) {
        val cam = camera
        if (cam == null) { then?.invoke(); return }
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x ?: (previewView.width / 2f), y ?: (previewView.height / 2f))
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        val future = cam.cameraControl.startFocusAndMetering(action)
        if (then != null) {
            // Auslösen, sobald scharf (oder spätestens nach dem Versuch – manche Kameras melden kein Ergebnis).
            future.addListener({ then() }, ContextCompat.getMainExecutor(context))
        }
    }

    /** [live]: grüner Rahmen beim Auslösen (0..1), Ersatz, falls im Foto selbst keine Seite gefunden wird. */
    fun capture(live: List<Pt>?) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        try {
                            val (scanPage, detected) = withContext(Dispatchers.Default) {
                                var bmp = image.use { img -> rotate(img.toBitmap(), img.imageInfo.rotationDegrees) }
                                // Erst auf Arbeitsgröße bringen (spart Speicher), dann Seite suchen und gerade ziehen.
                                val f = PdfPages.downscale(bmp.width, bmp.height, 3000)
                                if (f < 1.0) {
                                    val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
                                    bmp.recycle(); bmp = scaled
                                }
                                // Seite im Foto suchen; sonst den Live-Rahmen nehmen, den man beim Auslösen sah.
                                val quad = if (!openCvReady) null else
                                    runCatching { PageDetector.detect(bmp) }.getOrNull()
                                        ?: live?.let { q -> QuadMath.order(q.map { Pt(it.x * bmp.width, it.y * bmp.height) }) }
                                            ?.takeIf { QuadMath.plausibleCandidate(it, bmp.width, bmp.height) }
                                Log.i("Simplescan", "Seite: " + (quad?.let { "erkannt" } ?: "nicht erkannt"))
                                // Originalfoto behalten (für späteres Nachbessern der Ecken), dann Seite berechnen.
                                val photo = PageRenderer.savePhoto(context, bmp)
                                bmp.recycle()
                                PageRenderer.render(context, System.nanoTime(), photo, quad, 0, session.filter, focalRatio) to (quad != null)
                            }
                            pages.add(scanPage)
                            lastDetected = detected
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: OutOfMemoryError) {
                            error = "Zu wenig Speicher für dieses Foto."
                        } catch (e: Exception) {
                            error = "Foto konnte nicht gespeichert werden: ${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            busy = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    error = "Foto fehlgeschlagen: ${exception.message ?: "unbekannter Fehler"}"
                }
            },
        )
    }

    fun shoot() {
        if (busy) return
        busy = true
        val live = liveQuad
        tracker.disarm(live) // dieselbe Seite nicht gleich noch einmal automatisch
        focus { capture(liveQuad ?: live) }
    }

    // Automatisch auslösen, wenn die Seite ruhig im Rahmen liegt.
    LaunchedEffect(autoFire) { if (autoFire > 0 && autoOn && !busy) shoot() }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { offset -> focus(offset.x, offset.y) }
                },
            )
            // Grüner Rahmen um die erkannte Seite
            Canvas(Modifier.fillMaxSize()) {
                val q = liveQuad ?: return@Canvas
                // Vorschau ist „FIT_CENTER“: Bildfläche im Kasten berechnen.
                val boxRatio = size.width / size.height
                val (dw, dh) = if (boxRatio > liveAspect) size.height * liveAspect to size.height else size.width to size.width / liveAspect
                val ox = (size.width - dw) / 2
                val oy = (size.height - dh) / 2
                val path = Path().apply {
                    q.forEachIndexed { i, p ->
                        val x = ox + (p.x * dw).toFloat()
                        val y = oy + (p.y * dh).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(path, ScanGreen.copy(alpha = 0.28f))
                drawPath(path, ScanMint, style = Stroke(width = 3.dp.toPx()))
            }
            // Hinweis oben
            Text(
                when {
                    !openCvReady -> "Seitenerkennung nicht verfügbar"
                    busy -> "Foto wird verarbeitet …"
                    liveQuad != null && autoOn && steadying -> "Ruhig halten …"
                    liveQuad != null -> "Seite erkannt"
                    else -> "Seite ins Bild halten"
                },
                color = if (liveQuad != null) ScanGreen else Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(if (liveQuad != null) ScanMint else Color(0x99000000), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            error?.let {
                Text(
                    it, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp)
                        .clickable { error = null },
                )
            }
        }
        // Bedienleiste
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(ScanGreen)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { flash = !flash }) {
                    Icon(
                        if (flash) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = if (flash) "Blitz aus" else "Blitz an",
                        tint = if (flash) Color(0xFFF2C94C) else Color.White,
                    )
                }
                lastThumb?.let {
                    Image(
                        it, contentDescription = "Seitenübersicht öffnen",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, ScanMint, RoundedCornerShape(6.dp))
                            .clickable(enabled = !busy) { onShowPages() },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(if (pages.size == 1) "1 Seite" else "${pages.size} Seiten", color = ScanMint, fontSize = 14.sp)
                    lastDetected?.let {
                        Text(if (it) "zugeschnitten" else "ganzes Foto", color = ScanMint.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }
            // Auslöser
            Box(
                Modifier.size(76.dp).border(4.dp, ScanMint, CircleShape).padding(8.dp)
                    .clip(CircleShape).background(Color.White).clickable(enabled = !busy) { shoot() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = ScanGreen, strokeWidth = 3.dp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onShowPages, enabled = pages.isNotEmpty() && !busy) {
                    Text("Fertig", color = if (pages.isNotEmpty()) Color.White else ScanMint.copy(alpha = 0.5f), fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Brennweite geteilt durch die lange Sensorkante (aus den Kameradaten). Mal die lange Fotokante in Pixeln
 * ergibt die Brennweite in Pixeln. Null, wenn das Gerät die Daten nicht liefert oder sie unplausibel sind.
 */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun cameraFocalRatio(camera: Camera): Double? = runCatching {
    val info = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.cameraInfo)
    val focal = info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        ?.firstOrNull() ?: return@runCatching null
    val sensor = info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        ?: return@runCatching null
    (focal / kotlin.math.max(sensor.width, sensor.height)).toDouble().takeIf { it in 0.4..2.0 }
}.getOrNull()

/** Kamera-Dienst holen, ohne den Haupt-Thread zu blockieren. */
private suspend fun cameraProvider(context: android.content.Context): ProcessCameraProvider =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cont.resumeWith(Result.success(future.get()))
            } catch (e: Exception) {
                cont.resumeWith(Result.failure(e))
            }
        }, ContextCompat.getMainExecutor(context))
    }

private fun rotate(src: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
}
