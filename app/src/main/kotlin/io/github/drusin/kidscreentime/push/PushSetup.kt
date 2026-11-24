package io.github.drusin.kidscreentime.push

import android.app.Activity
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Registers this app at the UnifiedPush distributor (the ntfy app). Called whenever the app starts paired and
 * after the Kopplung: the distributor answers with an endpoint ([ResyncPushService.onNewEndpoint]), which also repairs
 * a registration that got lost. The connector generates the WebPush keys and keeps the private key on the device.
 *
 * Needs an Activity, because choosing a distributor may show the system picker. Without any distributor nothing
 * happens; the Einrichtungs-Checkliste (ticket 15) reports it via `UnifiedPush.getAckDistributor`.
 */
object PushSetup {
    fun register(activity: Activity) {
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { success ->
            if (success) UnifiedPush.register(activity)
        }
    }
}
