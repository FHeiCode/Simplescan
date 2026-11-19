package de.simplescan.app

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.simplescan.app.ui.MainScreen
import de.simplescan.app.ui.SimplescanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Der Kopf ist immer grün: helle Symbole in der Statusleiste.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            SimplescanTheme {
                MainScreen()
            }
        }
    }
}
