package io.github.drusin.kidscreentime.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import io.github.drusin.kidscreentime.R

/**
 * Short foreground service (type `systemExempted`) for the moment of the ring: sound over the alarm stream and the
 * full-screen intent to [AlarmActivity]. It ends by itself after [RING_LIMIT_MILLIS]; the Alarmkern has planned the
 * next repetition anyway. A Vorwarnung is the same service with a short sound and no full screen.
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                ringing = true
                enterForeground(ringNotification())
                play(RingtoneManager.TYPE_ALARM, looping = true)
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ stopSelf() }, RING_LIMIT_MILLIS)
            }
            ACTION_WARN -> {
                // A running ring is louder and wins.
                if (!ringing) {
                    enterForeground(warningNotification())
                    play(RingtoneManager.TYPE_NOTIFICATION, looping = false)
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ stopSelf() }, WARN_LIMIT_MILLIS)
                }
            }
            // "Ton aus": ends this ringing only, the next repetition stays planned.
            ACTION_SILENCE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ringing = false
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        super.onDestroy()
    }

    private fun enterForeground(notification: Notification) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
    }

    private fun play(type: Int, looping: Boolean) {
        ringtone?.stop()
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(type))?.apply {
            // The alarm stream: audible with the screen off, in the background and over "Nicht stören" alarm rules.
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = looping
            play()
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.alarm_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                // The sound comes from the service over the alarm stream, not from the notification.
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun alarmActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ringNotification(): Notification {
        ensureChannel()
        val silence = PendingIntent.getService(
            this,
            1,
            Intent(this, AlarmService::class.java).setAction(ACTION_SILENCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.alarm_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(alarmActivityIntent())
            .setFullScreenIntent(alarmActivityIntent(), true)
            .addAction(0, getString(R.string.alarm_silence_short), silence)
            .build()
    }

    private fun warningNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.warning_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(PendingIntent.getActivity(this, 2, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    companion object {
        const val ACTION_RING = "io.github.drusin.kidscreentime.alarm.RING"
        const val ACTION_WARN = "io.github.drusin.kidscreentime.alarm.WARN"
        const val ACTION_SILENCE = "io.github.drusin.kidscreentime.alarm.SILENCE"
        private const val CHANNEL_ID = "alarm"
        private const val NOTIFICATION_ID = 1
        private const val RING_LIMIT_MILLIS = 60_000L
        private const val WARN_LIMIT_MILLIS = 4_000L

        /** True while the service rings (same process as the Alarmkern). */
        @Volatile
        private var ringing = false

        fun start(context: Context, action: String) {
            context.startForegroundService(Intent(context, AlarmService::class.java).setAction(action))
        }

        /** Ends a ringing Alarm, leaves a Vorwarnung alone. */
        fun stopRinging(context: Context) {
            if (ringing) context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
