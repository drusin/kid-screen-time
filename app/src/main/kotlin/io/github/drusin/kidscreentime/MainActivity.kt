package io.github.drusin.kidscreentime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.push.PushSetup
import io.github.drusin.kidscreentime.ui.AppRoot
import io.github.drusin.kidscreentime.ui.theme.KidScreenTimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KidScreenTimeApp).container
        // Once paired (at start or right after the Kopplung): register at the push distributor and tell the server.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.pairingRepository.state.collect { state ->
                    if (state is DeviceState.Paired) {
                        PushSetup.register(this@MainActivity)
                        container.pushRepository.submit()
                    }
                }
            }
        }
        setContent {
            KidScreenTimeTheme {
                AppRoot(container)
            }
        }
    }
}
