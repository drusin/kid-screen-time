package io.github.drusin.kidscreentime.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Ausloeser
import io.github.drusin.kidscreentime.sync.SyncScenario
import io.github.drusin.kidscreentime.sync.syncScenario
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Records what the Alarmkern lets happen at the device. */
class RecordingAlarmEffects : AlarmEffects {
    val calls = mutableListOf<String>()
    override fun warn() { calls += "warn" }
    override fun ring() { calls += "ring" }
    override fun silence() { calls += "silence" }
}

/**
 * Ticket 06: the Alarmkern plans the right times with `setAlarmClock` (read back from `ShadowAlarmManager`) at Start,
 * Stopp, Sync with a changed Restzeit and after a reboot. Real Room, real server (sync scenario); only the sound is
 * recorded. The Neuberechnung of the times itself is tested in `domain` (`FaelligeZeitpunkteTest`).
 */
@RunWith(RobolectricTestRunner::class)
class AlarmKernTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val effects = RecordingAlarmEffects()

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice(alarmEffects = effects)
        sync()
        block()
    }

    private val alarmManager get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    /** The time of the one planned alarm clock, null when none is planned. */
    private fun planned(): Instant? = alarmManager.peekNextScheduledAlarm()?.let { Instant.ofEpochMilli(it.triggerAtMs) }

    private fun kernOf(scenario: SyncScenario) = scenario.child.app.container.alarmKern

    private suspend fun waitFor(what: String, condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    @Test
    fun `Beim Start plant der Alarmkern die Vorwarnung, nach dem Stopp storniert er`() = scenario {
        val start = server.clock.now()
        val id = start()

        kernOf(this).replan()
        assertEquals(start + Duration.ofMinutes(55), planned(), "Montag: 60 min Kontingent, Vorwarnung bei 5 min Restzeit")

        stop(id)
        kernOf(this).replan()
        assertNull(planned())
    }

    @Test
    fun `Die Beobachtung plant bei Start, Stopp und Sync ohne weiteren Aufruf neu`() = scenario {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        kernOf(this).observe(scope)
        val start = server.clock.now()

        val id = start()
        waitFor("Start geplant") { planned() == start + Duration.ofMinutes(55) }

        advance(Duration.ofMinutes(10))
        assertEquals(ChildSyncResult.Synced, sync())
        // The snapshot arrives with the Sitzung; nothing changes in the times, but the alarm is still planned.
        waitFor("nach dem Sync geplant") { planned() == start + Duration.ofMinutes(55) }

        stop(id)
        waitFor("Stopp storniert") { planned() == null }
        scope.cancel()
    }

    @Test
    fun `Nach dem Vorwarnzeitpunkt klingelt die Vorwarnung einmal, dann ist der Alarm als nächstes geplant`() = scenario {
        start()
        kernOf(this).replan()

        advance(Duration.ofMinutes(55))
        val due = kernOf(this).trigger()

        assertEquals(listOf(Ausloeser.VORWARNUNG), due)
        assertEquals(listOf("warn"), effects.calls.filter { it != "silence" })
        assertEquals(server.clock.now() + Duration.ofMinutes(5), planned(), "Der Alarm bei Restzeit 0")

        // Another plan (e.g. after a sync) must not bring the Vorwarnung back.
        kernOf(this).replan()
        assertEquals(server.clock.now() + Duration.ofMinutes(5), planned())
    }

    @Test
    fun `Der Alarm klingelt bei Restzeit 0 und wiederholt sich alle 5 Minuten`() = scenario {
        start()
        advance(Duration.ofMinutes(56))
        kernOf(this).trigger() // the Vorwarnung
        effects.calls.clear()

        advance(Duration.ofMinutes(4))
        assertEquals(listOf(Ausloeser.ALARM), kernOf(this).trigger())
        assertEquals(listOf("ring"), effects.calls.filter { it != "silence" })
        assertEquals(server.clock.now() + Duration.ofMinutes(5), planned())

        advance(Duration.ofMinutes(5))
        assertEquals(listOf(Ausloeser.ALARM), kernOf(this).trigger())
        assertEquals(server.clock.now() + Duration.ofMinutes(5), planned())
    }

    @Test
    fun `Ton aus ändert nichts an der Planung, die nächste Wiederholung bleibt geplant`() = scenario {
        start()
        advance(Duration.ofMinutes(61))
        kernOf(this).trigger()
        val nextRepetition = server.clock.now() + Duration.ofMinutes(5)

        effects.silence() // "Ton aus" only ends the ringing

        assertEquals(nextRepetition, planned())
    }

    @Test
    fun `Ist die Restzeit nach einem Sync 0 oder darunter, klingelt der Alarm sofort`() = scenario {
        start()
        sync()
        // Tagesanpassung while offline: the parents took 30 minutes away; the Kindgerät has run for 40 minutes.
        advance(Duration.ofMinutes(40))
        serverSql("INSERT INTO daily_adjustments (child_id, day, delta_minutes, booked_at) VALUES ('${child.childId}', '2026-01-05', -30, ${server.clock.now().toEpochMilli()})")

        assertEquals(ChildSyncResult.Synced, sync())
        kernOf(this).replan()

        assertEquals(server.clock.now(), planned(), "Sofort fällig")
        assertEquals(listOf(Ausloeser.ALARM), kernOf(this).trigger())
    }

    @Test
    fun `Ein Sync mit größerer Restzeit verschiebt den Alarm und beendet ein laufendes Klingeln`() = scenario {
        start()
        advance(Duration.ofMinutes(61))
        kernOf(this).trigger()
        effects.calls.clear()

        serverSql("INSERT INTO daily_adjustments (child_id, day, delta_minutes, booked_at) VALUES ('${child.childId}', '2026-01-05', 30, ${server.clock.now().toEpochMilli()})")
        sync()
        kernOf(this).replan()

        assertTrue("silence" in effects.calls, "Restzeit über 0: der Alarm verstummt")
        assertEquals(server.clock.now() + Duration.ofMinutes(29), planned())
    }

    @Test
    fun `Nach einem Neustart plant der Alarmkern aus dem lokalen Stand neu und kennt die Vorwarnung noch`() = scenario {
        start()
        advance(Duration.ofMinutes(56))
        kernOf(this).trigger() // Vorwarnung given
        AlarmScheduler(context).cancel()
        assertNull(planned(), "Neustart: alle Alarme sind weg")

        // New process: new Alarmkern (memory in SharedPreferences, stand in Room).
        val app = child.app.container
        val afterReboot = AlarmKern(app.childRepository, AlarmScheduler(context), SharedPreferencesAlarmMemory(context), effects, app.wallClock)
        afterReboot.replan()

        assertEquals(server.clock.now() + Duration.ofMinutes(4), planned(), "Der Alarm, nicht noch einmal die Vorwarnung")
    }

    @Test
    fun `Der Alarmkern plant den lokalen Automatischen Stopp als nächsten Zeitpunkt nach der letzten Wiederholung`() = scenario {
        val start = server.clock.now()
        start()
        advance(Duration.ofMinutes(114))
        kernOf(this).trigger() // overdue Alarm rings, the next repetition is planned after it
        assertEquals(server.clock.now() + Duration.ofMinutes(5), planned(), "Wiederholung nach 119 min, vor dem Stopp")

        advance(Duration.ofMinutes(5))
        kernOf(this).trigger()

        assertEquals(start + Duration.ofMinutes(120), planned(), "60 min Kontingent + 60 min Überziehungsgrenze: der Stopp, keine Wiederholung danach")
    }

    @Test
    fun `Der Automatische Stopp beendet die Sitzung lokal zum Zeitpunkt der Grenze, der Alarm hört auf und der Stand wird synchronisiert`() = scenario {
        val start = server.clock.now()
        val id = start()
        sync()
        advance(Duration.ofMinutes(125)) // the alarm clock fired late
        effects.calls.clear()

        assertEquals(listOf(Ausloeser.AUTOMATISCHER_STOPP), kernOf(this).trigger())

        assertEquals(setOf("silence"), effects.calls.toSet(), "Es klingelt nichts mehr, ein Klingeln endet")
        assertNull(planned(), "Keine Sitzung, nichts geplant")
        assertNull(kernOf(this).currentStand()?.laufendeSitzung)
        val ended = serverSession(id)
        assertEquals(start.toEpochMilli() + 120 * 60_000L, ended.second, "Ende = Zeitpunkt der Grenze, nicht der späte Alarm")
        assertTrue(child.outbox().isEmpty(), "Der Stopp ging über die Outbox und wurde zugestellt")
    }

    @Test
    fun `Das Manifest meldet den Boot-Receiver und den Alarm-Receiver`() {
        val pm = context.packageManager
        val boot = pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName), 0)
        assertTrue(boot.any { it.activityInfo.name == BootReceiver::class.java.name })
        val fire = pm.queryBroadcastReceivers(Intent(context, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_FIRE), 0)
        assertNotNull(fire.firstOrNull())
    }
}
