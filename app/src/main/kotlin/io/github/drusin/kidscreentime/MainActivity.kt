package io.github.drusin.kidscreentime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.drusin.kidscreentime.ui.PairDeviceScreen
import io.github.drusin.kidscreentime.ui.theme.KidScreenTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidScreenTimeTheme {
                PairDeviceScreen()
            }
        }
    }
}
