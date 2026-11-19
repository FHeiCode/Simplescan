package de.simplescan.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
