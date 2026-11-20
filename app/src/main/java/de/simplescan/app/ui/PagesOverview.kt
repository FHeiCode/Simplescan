package de.simplescan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF1F6F5C)
private val Mint = Color(0xFFB9E4D6)
private val Paper = Color(0xFFF4FAF7)

/** Schritt 3: alle Seiten im Überblick – Ecken anpassen, drehen, löschen, umsortieren. */
@Composable
fun PagesOverview(
    session: ScanSession,
    busyId: Long?,
    onEdit: (ScanPage) -> Unit,
    onRotate: (ScanPage) -> Unit,
    onAddMore: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onAddMore() }
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().background(Green).windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Scan verwerfen", tint = Color.White) }
            Text(
                if (session.pages.size == 1) "1 Seite" else "${session.pages.size} Seiten",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
        }

        if (session.pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Keine Seiten mehr. Nimm eine neue auf.", color = Color(0xFF3F4945))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(session.pages, key = { _, p -> p.id }) { index, page ->
                    PageTile(
                        page = page,
                        number = index + 1,
                        isFirst = index == 0,
                        isLast = index == session.pages.lastIndex,
                        busy = busyId == page.id,
                        onEdit = { onEdit(page) },
                        onRotate = { onRotate(page) },
                        onDelete = { session.remove(page.id) },
                        onLeft = { session.move(page.id, -1) },
                        onRight = { session.move(page.id, +1) },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .background(Green, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onAddMore, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.AddAPhoto, null); Text("  Weitere Seite")
            }
            Button(
                onClick = onFinish, enabled = session.pages.isNotEmpty() && busyId == null, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Green),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, null); Text("  PDF erstellen")
            }
        }
    }
}

@Composable
private fun PageTile(
    page: ScanPage,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, Mint, RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clickable(enabled = !busy, onClick = onEdit).padding(8.dp)) {
            Image(page.thumb, "Seite $number", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Text(
                "$number", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).background(Green, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            if (busy) {
                Text(
                    "wird berechnet …", color = Green, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center).background(Mint, RoundedCornerShape(8.dp)).padding(6.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onLeft, enabled = !isFirst && !busy) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Nach vorn", tint = if (isFirst) Mint else Green)
            }
            IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Outlined.Crop, "Ecken anpassen", tint = Green) }
            IconButton(onClick = onRotate, enabled = !busy) { Icon(Icons.Outlined.RotateRight, "Drehen", tint = Green) }
            IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Outlined.Delete, "Löschen", tint = Green) }
            IconButton(onClick = onRight, enabled = !isLast && !busy) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Nach hinten", tint = if (isLast) Mint else Green)
            }
        }
    }
}
