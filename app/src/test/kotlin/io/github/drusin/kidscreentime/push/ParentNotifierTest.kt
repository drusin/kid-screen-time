package io.github.drusin.kidscreentime.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Ticket 14: the notification itself: own channel with high importance (Heads-up), text from the signal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParentNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `Die Benachrichtigung steht in einem eigenen Kanal mit hoher Wichtigkeit und trägt den Text`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        AndroidParentNotifier(context).show("Bildschirmzeit", "Mia: Automatischer Stopp nach 60 min Überziehung")

        val channel = assertNotNull(manager.getNotificationChannel(AndroidParentNotifier.CHANNEL_ID))
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Bildschirmzeit", posted.extras.getString("android.title"))
        assertEquals("Mia: Automatischer Stopp nach 60 min Überziehung", posted.extras.getCharSequence("android.text").toString())
        assertEquals(AndroidParentNotifier.CHANNEL_ID, posted.channelId)
    }

    @Test
    fun `Zwei Benachrichtigungen stehen nebeneinander`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidParentNotifier(context)

        notifier.show("Bildschirmzeit", "Mia: Kontingent abgelaufen (seit 10:00)")
        notifier.show("Bildschirmzeit", "Mia: Automatischer Stopp nach 60 min Überziehung")

        assertEquals(2, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `Ohne die Berechtigung erscheint nichts`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        AndroidParentNotifier(context).show("Bildschirmzeit", "Mia: Kontingent abgelaufen (seit 10:00)")

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
