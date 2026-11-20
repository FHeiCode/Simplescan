package de.simplescan.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simplescan.app.data.EditGeometry
import de.simplescan.app.data.PageDetector
import de.simplescan.app.data.Pt
import de.simplescan.app.data.QuadMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private val Green = Color(0xFF1F6F5C)
private val Mint = Color(0xFFB9E4D6)
private val Yellow = Color(0xFFF2C94C)

/**
 * Schritt 3: Ecken einer Seite von Hand setzen. Zeigt das ganze Originalfoto mit vier Griffpunkten;
 * beim Ziehen erscheint eine Lupe. Ecken werden in Foto-Pixeln an [onApply] gegeben (null = ganzes Foto).
 */
@Composable
fun CornerEditor(page: ScanPage, openCvReady: Boolean, onApply: (List<Pt>?) -> Unit, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    // Foto in Bildschirmgröße laden (spart Speicher); Ecken werden in diesen Maßstab umgerechnet.
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var factor by remember { mutableStateOf(1.0) } // Foto-Pixel je Anzeige-Pixel
    var corners by remember { mutableStateOf<List<Pt>?>(null) } // in Pixeln des geladenen Bildes
    var busy by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(page.id) {
        val (img, f) = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = max(1, max(page.photoWidth, page.photoHeight) / 1600)
            }
            val bmp = BitmapFactory.decodeFile(page.photo.absolutePath, opts)
            bmp.asImageBitmap() to page.photoWidth.toDouble() / bmp.width
        }
        image = img
        factor = f
        corners = (page.quad ?: EditGeometry.fullFrame(page.photoWidth, page.photoHeight)).map { Pt(it.x / f, it.y / f) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Green).windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "Abbrechen", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Ecken anpassen", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Grüne Punkte auf die Ecken der Seite ziehen", color = Mint, fontSize = 12.sp)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val img = image
            val cs = corners
            if (img == null || cs == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Mint)
            } else {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(img) {
                        val fit = EditGeometry.fit(img.width, img.height, size.width.toDouble(), size.height.toDouble())
                        detectDragGestures(
                            onDragStart = { o ->
                                val inView = (corners ?: return@detectDragGestures).map { fit.toView(it) }
                                dragging = EditGeometry.hitCorner(inView, o.x.toDouble(), o.y.toDouble(), 48.dp.toPx().toDouble())
                                finger = o
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                            onDrag = { change, _ ->
                                val i = dragging ?: return@detectDragGestures
                                change.consume()
                                finger = change.position
                                val p = EditGeometry.clamp(
                                    fit.toImage(change.position.x.toDouble(), change.position.y.toDouble()), img.width, img.height,
                                )
                                corners = corners?.toMutableList()?.also { it[i] = p }
                            },
                        )
                    },
                ) {
                    val fit = EditGeometry.fit(img.width, img.height, size.width.toDouble(), size.height.toDouble())
                    val dstW = (img.width * fit.scale).roundToInt()
                    val dstH = (img.height * fit.scale).roundToInt()
                    drawImage(
                        img, dstOffset = IntOffset(fit.offsetX.roundToInt(), fit.offsetY.roundToInt()),
                        dstSize = IntSize(dstW, dstH),
                    )
                    val v = cs.map { fit.toView(it) }
                    val path = Path().apply {
                        v.forEachIndexed { i, p -> if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat()) else lineTo(p.x.toFloat(), p.y.toFloat()) }
                        close()
                    }
                    drawPath(path, Green.copy(alpha = 0.25f))
                    drawPath(path, Mint, style = Stroke(2.dp.toPx()))
                    v.forEachIndexed { i, p ->
                        val c = Offset(p.x.toFloat(), p.y.toFloat())
                        drawCircle(if (dragging == i) Yellow else Mint, radius = 14.dp.toPx(), center = c)
                        drawCircle(Green, radius = 14.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                    }
                    // Lupe: vergrößerter Ausschnitt um den Finger, in der Ecke gegenüber.
                    val di = dragging
                    if (di != null) {
                        val r = 64.dp.toPx()
                        val zoom = 2.5f
                        val center = Offset(
                            if (finger.x < size.width / 2) size.width - r - 8.dp.toPx() else r + 8.dp.toPx(),
                            r + 8.dp.toPx(),
                        )
                        val src = cs[di]
                        val half = r / zoom / fit.scale.toFloat() // halbe Ausschnittbreite in Bildpixeln
                        val x0 = (src.x.toFloat() - half).coerceIn(0f, img.width - 1f)
                        val y0 = (src.y.toFloat() - half).coerceIn(0f, img.height - 1f)
                        val x1 = (src.x.toFloat() + half).coerceIn(x0 + 1f, img.width.toFloat())
                        val y1 = (src.y.toFloat() + half).coerceIn(y0 + 1f, img.height.toFloat())
                        val k = zoom * fit.scale.toFloat() // Bildpixel → Lupenpixel
                        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                        clipPath(circle) {
                            drawRect(Color.Black, topLeft = center - Offset(r, r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
                            drawImage(
                                img,
                                srcOffset = IntOffset(x0.roundToInt(), y0.roundToInt()),
                                srcSize = IntSize((x1 - x0).roundToInt().coerceAtLeast(1), (y1 - y0).roundToInt().coerceAtLeast(1)),
                                dstOffset = IntOffset(
                                    (center.x + (x0 - src.x.toFloat()) * k).roundToInt(),
                                    (center.y + (y0 - src.y.toFloat()) * k).roundToInt(),
                                ),
                                dstSize = IntSize(((x1 - x0) * k).roundToInt().coerceAtLeast(1), ((y1 - y0) * k).roundToInt().coerceAtLeast(1)),
                            )
                        }
                        drawCircle(Mint, radius = r, center = center, style = Stroke(3.dp.toPx()))
                        drawLine(Yellow, center - Offset(10.dp.toPx(), 0f), center + Offset(10.dp.toPx(), 0f), 2.dp.toPx())
                        drawLine(Yellow, center - Offset(0f, 10.dp.toPx()), center + Offset(0f, 10.dp.toPx()), 2.dp.toPx())
                    }
                }
            }
        }

        val img = image
        Column(
            Modifier.fillMaxWidth()
                .background(Green, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (img != null) corners = EditGeometry.fullFrame(img.width, img.height)
                    },
                    enabled = img != null && !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Ganzes Foto") }
                OutlinedButton(
                    onClick = { busy = true },
                    enabled = img != null && openCvReady && !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Automatisch") }
            }
            Button(
                onClick = {
                    val cs = corners ?: return@Button
                    val f = factor
                    val inPhoto = QuadMath.order(cs.map { Pt(it.x * f, it.y * f) })
                    val full = img != null && cs == EditGeometry.fullFrame(img.width, img.height)
                    onApply(if (full || !QuadMath.isConvex(inPhoto)) null else inPhoto)
                },
                enabled = corners != null && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Green),
            ) { Text("Übernehmen") }
        }
    }

    // „Automatisch“: Seite im geladenen Bild neu suchen.
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        val img = image
        if (img != null) {
            val found = withContext(Dispatchers.Default) {
                val bmp = android.graphics.Bitmap.createBitmap(img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
                val pixels = IntArray(img.width * img.height)
                img.readPixels(pixels)
                bmp.setPixels(pixels, 0, img.width, 0, 0, img.width, img.height)
                runCatching { PageDetector.detect(bmp, 900) ?: PageDetector.detect(bmp, 480) }.getOrNull().also { bmp.recycle() }
            }
            if (found != null) corners = found
        }
        busy = false
    }
}
