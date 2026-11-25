package io.github.drusin.kidscreentime.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.drusin.kidscreentime.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shows an Eltern-Benachrichtigung (ticket 14): the text comes ready-made in the push signal, so showing it needs no
 * network access of the app (Doze). Tests record instead of posting.
 */
fun interface ParentNotifier {
    fun show(title: String, text: String)
}

/** Heads-up notification in the own channel [CHANNEL_ID] (high importance). Without the permission `POST_NOTIFICATIONS` nothing shows. */
class AndroidParentNotifier(private val context: Context) : ParentNotifier {
    private val nextId = AtomicInteger(FIRST_ID)

    // The permission is checked right here; lint does not see it through the helper.
    @SuppressLint("MissingPermission")
    override fun show(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.parent_notification_channel_name), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // A new id each time: two notifications (Alarm, then Automatischer Stopp) stay side by side.
        manager.notify(nextId.getAndIncrement(), notification)
    }

    companion object {
        const val CHANNEL_ID = "parent-notifications"
        private const val FIRST_ID = 100
    }
}
