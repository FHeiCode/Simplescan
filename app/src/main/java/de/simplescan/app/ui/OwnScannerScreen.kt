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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File

private val ScanGreen = Color(0xFF1F6F5C)
private val ScanMint = Color(0xFFB9E4D6)

/**
 * Versuch „eigener Scanner“, Schritt 1: eigene Kamera-Ansicht im App-Grün, Auslöser, Blitz,
 * mehrere Seiten. Noch ohne Seitenerkennung – die Fotos kommen unverändert ins PDF.
 */
@Composable
fun OwnScannerScreen(onDone: (List<File>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val pages = remember { mutableStateListOf<File>() }
    fun close() {
        pages.forEach { it.delete() }
        onClose()
    }
    BackHandler { close() }

    // Test: Wird OpenCV (für die Seitenerkennung in Schritt 2) auf diesem Gerät geladen?
    val openCvReady = remember { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Kopf
        Row(
            Modifier.fillMaxWidth().background(ScanGreen).windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { close() }) { Icon(Icons.Outlined.Close, "Abbrechen", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Eigener Scanner", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                Text(
                    if (openCvReady) "Test · Bildverarbeitung bereit" else "Test · Bildverarbeitung nicht geladen",
                    color = ScanMint, fontSize = 12.sp,
                )
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
                pages = pages,
                onDone = { onDone(pages.toList()) },
            )
        }
    }
}

@Composable
private fun CameraArea(modifier: Modifier, pages: MutableList<File>, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val provider = cameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Simplescan", "Kamera", e)
            error = "Kamera konnte nicht gestartet werden: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
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

    fun capture() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val bmp = image.use { img -> rotate(img.toBitmap(), img.imageInfo.rotationDegrees) }
                                PdfBuilder.savePage(context, bmp).also { bmp.recycle() }
                            }
                            pages.add(file)
                        } catch (e: CancellationException) {
                            throw e
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
        focus { capture() }
    }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { offset -> focus(offset.x, offset.y) }
                },
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
                Text(
                    if (pages.size == 1) "1 Seite" else "${pages.size} Seiten",
                    color = ScanMint, fontSize = 14.sp,
                )
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
                TextButton(onClick = onDone, enabled = pages.isNotEmpty() && !busy) {
                    Text("Fertig", color = if (pages.isNotEmpty()) Color.White else ScanMint.copy(alpha = 0.5f), fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}

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
