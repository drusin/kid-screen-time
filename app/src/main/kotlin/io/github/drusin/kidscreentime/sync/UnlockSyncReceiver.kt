package io.github.drusin.kidscreentime.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.drusin.kidscreentime.KidScreenTimeApp
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.pairing.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Syncs when the user unlocks the device (Sync-Protokoll: "beim Entsperren des Geräts"). `USER_PRESENT` cannot be
 * declared in the manifest, so [KidScreenTimeApp] registers this receiver in `onCreate`; it lives as long as the
 * process (which every alarm, push message and boot starts). Only a Kindgerät syncs.
 */
class UnlockSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val container = (context.applicationContext as KidScreenTimeApp).container
        val isChildDevice = (container.pairingRepository.state.value as? DeviceState.Paired)?.device?.role == DeviceRole.CHILD
        if (!isChildDevice) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.childRepository.sync()
            } finally {
                result?.finish() // null when not delivered as a broadcast (tests)
            }
        }
    }

    companion object {
        fun register(context: Context) {
            ContextCompat.registerReceiver(
                context,
                UnlockSyncReceiver(),
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}
