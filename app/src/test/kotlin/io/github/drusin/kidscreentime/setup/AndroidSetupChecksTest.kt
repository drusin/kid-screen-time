package io.github.drusin.kidscreentime.setup

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.api.SetupItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Ticket 15: the real system checks. Robolectric models four of them (exact alarms, notifications, battery, alarm
 * volume), those are steered here through the shadows. The Full-Screen-Intent (`canUseFullScreenIntent`) and the
 * Push-Verteiler are not modelled in a useful way: tests of the Checkliste steer them through the `SetupChecks` interface
 * (`FakeSetupChecks`), and the real ones belong in the manual checklist (`docs/alarm-checkliste.md`, Punkt 13).
 */
@RunWith(RobolectricTestRunner::class)
class AndroidSetupChecksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val checks = AndroidSetupChecks(context)

    private fun allInOrder() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)
        context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
    }

    /** Only the points Robolectric models; the other two are not the subject here. */
    private fun modelled() = checks.missing().filter { it in MODELLED }

    @Test
    fun `Sind Exakte Alarme, Benachrichtigungen, Akku und Alarmlautstärke in Ordnung, fehlt keiner davon`() {
        allInOrder()

        assertEquals(emptyList(), modelled())
    }

    @Test
    fun `Ohne Erlaubnis für Exakte Alarme fehlt genau dieser Punkt`() {
        allInOrder()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertEquals(listOf(SetupItem.EXACT_ALARMS), modelled())
    }

    @Test
    fun `Abgeschaltete Benachrichtigungen fehlen`() {
        allInOrder()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)

        assertEquals(listOf(SetupItem.NOTIFICATIONS), modelled())
    }

    @Test
    fun `Ein eingeschränkter Akku fehlt`() {
        allInOrder()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, false)

        assertEquals(listOf(SetupItem.BATTERY), modelled())
    }

    @Test
    fun `Alarmlautstärke 0 fehlt, größer als 0 ist in Ordnung`() {
        allInOrder()
        val audio = context.getSystemService(AudioManager::class.java)

        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertTrue(SetupItem.ALARM_VOLUME in checks.missing())

        audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        assertFalse(SetupItem.ALARM_VOLUME in checks.missing())
    }

    @Test
    fun `Ohne verbundenen Push-Verteiler fehlt dieser Punkt`() {
        assertTrue(SetupItem.PUSH_DISTRIBUTOR in checks.missing(), "In der Testumgebung ist kein Verteiler verbunden")
    }

    private companion object {
        val MODELLED = setOf(SetupItem.EXACT_ALARMS, SetupItem.NOTIFICATIONS, SetupItem.BATTERY, SetupItem.ALARM_VOLUME)
    }
}
