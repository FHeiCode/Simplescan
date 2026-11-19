package de.simplescan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.simplescan.app.ui.MainScreen
import de.simplescan.app.ui.SimplescanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SimplescanTheme {
                MainScreen()
            }
        }
    }
}
