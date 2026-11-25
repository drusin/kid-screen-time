package io.github.drusin.kidscreentime.setup

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import io.github.drusin.kidscreentime.api.SetupItem
import org.unifiedpush.android.connector.UnifiedPush

/**
 * The system checks of the Einrichtungs-Checkliste, behind one interface so that tests steer them (a fake in
 * `SetupFixtures`; [AndroidSetupChecks] is tested separately with the shadows Robolectric offers).
 */
fun interface SetupChecks {
    /** The prüfbaren Punkte that are missing right now, in the order of [SetupItem]. */
    fun missing(): List<SetupItem>
}

/**
 * The real checks, one system call each. An item whose system API does not exist at this Android version counts as in order.
 * "Akku Nicht eingeschränkt" is `isIgnoringBatteryOptimizations`: from Android 12 on that is true only for the setting
 * "Nicht eingeschränkt" (the default "Optimiert" reports false), which is what the Alarm needs.
 */
class AndroidSetupChecks(private val context: Context) : SetupChecks {
    override fun missing(): List<SetupItem> = SetupItem.entries.filterNot(::inOrder)

    private fun inOrder(item: SetupItem): Boolean = when (item) {
        SetupItem.EXACT_ALARMS -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        SetupItem.FULL_SCREEN_INTENT ->
            Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        SetupItem.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        SetupItem.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        SetupItem.ALARM_VOLUME -> context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) > 0
        SetupItem.PUSH_DISTRIBUTOR -> UnifiedPush.getAckDistributor(context) != null
    }
}
