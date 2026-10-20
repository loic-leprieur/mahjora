package fr.mahjora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.mahjora.ui.MahjoraNav
import fr.mahjora.ui.MahjoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MahjoraApp).container
        setContent {
            MahjoraTheme { MahjoraNav(container) }
        }
    }
}
