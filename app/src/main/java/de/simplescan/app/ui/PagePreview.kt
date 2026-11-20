package de.simplescan.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simplescan.app.data.PageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Green = Color(0xFF1F6F5C)
private val Mint = Color(0xFFB9E4D6)

/**
 * Große Ansicht einer Seite im oben gewählten Filter [filter] (z. B. Schwarzweiß), mit Zoomen per
 * Zwei-Finger-Geste und Doppeltipp. Hat die Seite schon diesen Filter, wird die fertige Seite gezeigt,
 * sonst eine Vorschau berechnet (ohne die Seite zu ändern).
 */
@Composable
fun PagePreview(page: ScanPage, number: Int, filter: PageFilter, onEditCorners: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page.id, page.rendered, filter) {
        image = null
        try {
            image = withContext(Dispatchers.Default) {
                val bmp = if (page.filter == filter) {
                    BitmapFactory.decodeFile(page.rendered.absolutePath)
                } else {
                    PageRenderer.pageBitmap(page.photo, page.quad, page.rotation, filter)
                }
                bmp.asImageBitmap()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = "Vorschau nicht möglich: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F1513))) {
        Row(
            Modifier.fillMaxWidth().background(Green).windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Seite $number", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Ansicht: ${filter.label} · zum Zoomen mit zwei Fingern ziehen", color = Mint, fontSize = 12.sp)
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .transformable(transform)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 3f
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            val img = image
            when {
                img != null -> Image(
                    img, "Seite $number",
                    modifier = Modifier.fillMaxSize().padding(12.dp).graphicsLayer(
                        scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y,
                    ),
                )
                error != null -> Text(error!!, color = Color.White, modifier = Modifier.padding(24.dp))
                else -> CircularProgressIndicator(color = Mint)
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .background(Green, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = onEditCorners,
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Green),
            ) { Icon(Icons.Outlined.Crop, null); Text("  Ecken anpassen") }
        }
    }
}
