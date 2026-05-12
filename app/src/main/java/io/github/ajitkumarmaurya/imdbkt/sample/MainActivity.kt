package io.github.ajitkumarmaurya.imdbkt.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.ajitkumarmaurya.imdbkt.sample.ui.navigation.ImdbNavHost
import io.github.ajitkumarmaurya.imdbkt.sample.ui.theme.ImdbKtTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImdbKtTheme {
                ImdbNavHost()
            }
        }
    }
}
