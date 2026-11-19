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

// Farbschema rund um das Grün aus dem Logo (#1F6F5C), Gelb als kleiner Akzent.
private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8DD),
    onSecondaryContainer = Color(0xFF062019),
    tertiary = Color(0xFF7A5D00),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF4FAF7),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF5F1),
    surfaceContainer = Color(0xFFE8F0EC),
    surfaceContainerHigh = Color(0xFFE2EAE6),
    surfaceContainerHighest = Color(0xFFDCE5E0),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF075143),
    onPrimaryContainer = Mint,
    secondary = Color(0xFFB0CCC1),
    onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCCE8DD),
    tertiary = Yellow,
    tertiaryContainer = Color(0xFF5C4600),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
)

@Composable
fun SimplescanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Immer die eigenen App-Farben (keine Hintergrundbild-Farben), damit alles zum grünen Logo passt.
    val scheme = if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}
