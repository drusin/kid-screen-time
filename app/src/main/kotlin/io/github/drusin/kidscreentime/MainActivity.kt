package io.github.drusin.kidscreentime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.drusin.kidscreentime.api.DeviceRole
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
    // The system shows the dialog at most twice; a refusal is final and the Eltern are not nagged (the checklist, ticket 15, reports it at the Kindgerät).
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
                        // Eltern-Benachrichtigungen (ticket 14) need the permission to show notifications.
                        if (state.device.role == DeviceRole.PARENT) requestNotificationPermission()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
