package io.github.drusin.kidscreentime.alarm

import android.app.AlarmManager
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.drusin.kidscreentime.MainActivity
import java.time.Instant

/**
 * The only place that talks to the `AlarmManager`: exactly one alarm clock (`setAlarmClock`, allowed by
 * `USE_EXACT_ALARM`) for the next due time of the [AlarmKern]. Robolectric tests read it back through
 * `ShadowAlarmManager.peekNextScheduledAlarm()`.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Replaces the planned alarm by one at [at]. A time that has passed fires at once. Lint wants SCHEDULE_EXACT_ALARM,
     * but `USE_EXACT_ALARM` (manifest) is granted at install for alarm clock apps, which is what this app is.
     */
    @SuppressLint("MissingPermission")
    fun schedule(at: Instant) {
        val showIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), showIntent), firePendingIntent())
    }

    fun cancel() = alarmManager.cancel(firePendingIntent())

    private fun firePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_FIRE = "io.github.drusin.kidscreentime.alarm.FIRE"
    }
}
