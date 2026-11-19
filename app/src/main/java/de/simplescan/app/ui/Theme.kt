package de.simplescan.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1F6F5C)
private val Mint = Color(0xFFB9E4D6)
private val Yellow = Color(0xFFF2C94C)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF7A5D00),
    secondaryContainer = Color(0xFFFFE08A),
    tertiary = Yellow,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF075143),
    onPrimaryContainer = Mint,
    tertiary = Yellow,
)

@Composable
fun SimplescanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Immer die eigenen App-Farben (keine Hintergrundbild-Farben), damit alles zum grünen Logo passt.
    val scheme = if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}
