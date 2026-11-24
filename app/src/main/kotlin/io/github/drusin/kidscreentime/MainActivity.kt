package io.github.drusin.kidscreentime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.drusin.kidscreentime.ui.AppRoot
import io.github.drusin.kidscreentime.ui.theme.KidScreenTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KidScreenTimeApp).container
        setContent {
            KidScreenTimeTheme {
                AppRoot(container)
            }
        }
    }
}
