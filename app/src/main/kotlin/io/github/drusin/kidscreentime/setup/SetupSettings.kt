package io.github.drusin.kidscreentime.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.drusin.kidscreentime.api.SetupItem

/**
 * The direct links of the Checkliste into the system settings. Only intents that need no extra permission and raise no
 * Play-policy question: for the battery the page of the app (App-Info, there "Akku" → "Nicht eingeschränkt") instead of
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (a Play restriction;
 * lint flags it as `BatteryLife`).
 */
object SetupSettings {
    /** The settings screen for [item], null for the Push-Verteiler (there is no system page; the screen opens the distributor picker). */
    fun intentFor(context: Context, item: SetupItem): Intent? {
        val app = Uri.fromParts("package", context.packageName, null)
        return when (item) {
            SetupItem.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app)
            SetupItem.FULL_SCREEN_INTENT ->
                if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app) else notificationSettings(context)
            SetupItem.NOTIFICATIONS -> notificationSettings(context)
            SetupItem.BATTERY -> appDetails(context)
            SetupItem.ALARM_VOLUME -> Intent(Settings.ACTION_SOUND_SETTINGS)
            SetupItem.PUSH_DISTRIBUTOR -> null
        }
    }

    /** Opens [item]'s settings; when the device has no such screen the App-Info page opens. Returns false if nothing could be opened. */
    fun open(context: Context, item: SetupItem): Boolean {
        val intent = intentFor(context, item) ?: return false
        return start(context, intent) || start(context, appDetails(context))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun notificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
