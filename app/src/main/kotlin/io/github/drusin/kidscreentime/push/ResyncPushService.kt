package io.github.drusin.kidscreentime.push

import android.util.Log
import io.github.drusin.kidscreentime.KidScreenTimeApp
import io.github.drusin.kidscreentime.api.PushRegistration
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Receives the events of the UnifiedPush distributor (the ntfy app). The connector decrypts WebPush messages with
 * the keys it generated at the registration and keeps the service bound while work runs, so the work goes to the
 * app's own scope. The logic lives in [PushHandler] and [PushRepository].
 */
class ResyncPushService : PushService() {
    private val container get() = (application as KidScreenTimeApp).container

    override fun onMessage(message: PushMessage, instance: String) {
        container.applicationScope.launch { container.pushHandler.onMessage(message.content, message.decrypted) }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet
        if (keys == null) {
            // Without WebPush keys the server could not encrypt (and sends nothing). The checklist (ticket 15) shows it.
            Log.w(TAG, "Der Push-Verteiler liefert keine WebPush-Schlüssel")
            return
        }
        container.applicationScope.launch {
            container.pushRepository.onNewEndpoint(PushRegistration(endpoint.url, keys.pubKey, keys.auth))
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        // Tried again at the next app start (PushSetup).
        Log.w(TAG, "Push-Registrierung fehlgeschlagen: $reason")
    }

    override fun onUnregistered(instance: String) {
        container.pushRepository.onUnregistered()
    }

    private companion object {
        const val TAG = "ResyncPushService"
    }
}
