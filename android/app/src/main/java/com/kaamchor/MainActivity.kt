package com.kaamchor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.kaamchor.ui.nav.KaamChorNav
import com.kaamchor.ui.theme.KaamChorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaamChorTheme {
                KaamChorNav()
            }
        }
    }
}
