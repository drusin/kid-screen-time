package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.child.toKindStand
import io.github.drusin.kidscreentime.db.OutboxSessionEntity
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ticket 08: without network the child starts and stops like always; when the network is back the Sitzungen land
 * correctly at the server (Sync-Protokoll: Outbox, Zeit als Alter, "offline erfasst", Kappen, frühere Ende gewinnt).
 */
@RunWith(RobolectricTestRunner::class)
class OfflineSyncScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val minute = 60_000L

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        sync() // the first snapshot, as at the start of the app
        requests.clear()
        block()
    }

    @Test
    fun `Offline gestartet und gestoppt landet die Sitzung später korrekt am Server und ist offline erfasst`() = scenario {
        val t0 = server.clock.now().toEpochMilli()
        online = false
        val id = start()
        assertEquals(ChildSyncResult.Unavailable, sync())
        advance(Duration.ofMinutes(20))
        stop(id)
        advance(Duration.ofMinutes(5))
        assertEquals(ChildSyncResult.Unavailable, sync())
        assertEquals(1, child.outbox().size)

        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(t0, t0 + 20 * minute, "STOPPED"), serverSession(id))
        assertTrue(serverRecordedOffline(id), "Der Server erfuhr den Start erst 25 min später")
        assertTrue(child.outbox().isEmpty())
        val stored = child.storedSnapshot()!!
        assertTrue(stored.todaySessions.single().recordedOffline)
        assertEquals(Duration.ofMinutes(20), stored.toKindStand().tagesstand(server.clock.now()).verbrauch)
    }

    @Test
    fun `Ein Start, der sofort ankommt, ist nicht offline erfasst`() = scenario {
        val id = start()
        assertEquals(ChildSyncResult.Synced, sync())
        advance(Duration.ofMinutes(10))
        stop(id)
        sync()

        assertFalse(serverRecordedOffline(id))
    }

    @Test
    fun `Ein fehlgeschlagener Sync fordert die Wiederholung durch den WorkManager an, ein gelungener nicht`() = scenario {
        online = false
        start()
        sync()
        sync()
        assertEquals(2, child.app.retriesScheduled)

        online = true
        sync()
        assertEquals(2, child.app.retriesScheduled)
    }

    @Test
    fun `Der Batch enthält alle offenen Sitzungen in Startreihenfolge`() = scenario {
        val t0 = server.clock.now().toEpochMilli()
        online = false
        val first = start()
        advance(Duration.ofMinutes(10))
        stop(first)
        advance(Duration.ofMinutes(10))
        val second = start()
        advance(Duration.ofMinutes(10))
        stop(second)
        advance(Duration.ofMinutes(10))
        val third = start()
        sync()
        requests.clear()

        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(listOf("POST ${ApiPaths.CHILD_SYNC}"), requests, "Ein Request für alle drei")
        assertEquals(
            listOf(first, second, third),
            serverSnapshot().todaySessions.map { it.id },
        )
        assertEquals(Triple(t0 + 20 * minute, t0 + 30 * minute, "STOPPED"), serverSession(second))
        assertEquals(Triple(t0 + 40 * minute, null, null), serverSession(third))
        assertTrue(child.outbox().isEmpty())
    }

    @Test
    fun `Ein doppelter Batch ist wirkungslos, auch wenn die Antwort verloren ging`() = scenario {
        online = false
        val id = start()
        advance(Duration.ofMinutes(15))
        stop(id)
        advance(Duration.ofMinutes(3))
        val lost = child.outbox().single()
        online = true
        sync()
        val stored = serverSession(id)
        val snapshot = serverSnapshot()

        // The answer got lost: the device still has the row and sends it again, later (its ages are bigger now).
        advance(Duration.ofMinutes(7))
        child.app.database.outboxDao().upsert(lost)
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(stored, serverSession(id))
        assertEquals(snapshot.todaySessions, serverSnapshot().todaySessions)
        assertTrue(child.outbox().isEmpty())
    }

    @Test
    fun `Der Server kappt einen Start auf das Ende der vorigen Sitzung`() = scenario {
        val t0 = server.clock.now().toEpochMilli()
        val first = start()
        advance(Duration.ofMinutes(30))
        stop(first)
        sync()
        advance(Duration.ofMinutes(10))

        // A skewed device: its second Sitzung claims to have started 5 min before the first one ended.
        val skewed = java.util.UUID.randomUUID().toString()
        val mono = server.clock.elapsed().toMillis()
        val wall = server.clock.now().toEpochMilli()
        child.app.database.outboxDao().upsert(OutboxSessionEntity(skewed, mono - 15 * minute, wall - 15 * minute))
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(t0 + 30 * minute, serverSession(skewed).first, "Kein Überlappen mit der ersten Sitzung")
        assertNull(serverSession(skewed).second)
    }

    @Test
    fun `Der Server kappt Zeitpunkte in der Zukunft auf jetzt`() = scenario {
        val now = server.clock.now().toEpochMilli()
        val future = java.util.UUID.randomUUID().toString()
        val mono = server.clock.elapsed().toMillis()
        // Monotonic and wall clock of the device say "in 10 minutes": the age is negative.
        child.app.database.outboxDao().upsert(
            OutboxSessionEntity(future, mono + 10 * minute, now + 10 * minute, mono + 12 * minute, now + 12 * minute),
        )
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(now, now, "STOPPED"), serverSession(future))
    }

    @Test
    fun `Liegt ein Neustart zwischen Zeitpunkt und Sync, zählt die Wanduhrzeit`() = scenario {
        advance(Duration.ofMinutes(30)) // the device has been running for a while: its monotonic values are big
        val t0 = server.clock.now().toEpochMilli()
        online = false
        val id = start()
        advance(Duration.ofMinutes(4))
        reboot(Duration.ofMinutes(10)) // the monotonic clock starts at zero again
        advance(Duration.ofMinutes(1))

        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(t0, null, null), serverSession(id), "Der Start liegt 15 min zurück, nicht bei 0")
        assertTrue(serverRecordedOffline(id))
    }

    @Test
    fun `Start vor und Stopp nach dem Neustart kommen beide richtig an`() = scenario {
        advance(Duration.ofMinutes(30))
        val t0 = server.clock.now().toEpochMilli()
        online = false
        val id = start()
        advance(Duration.ofMinutes(4))
        reboot(Duration.ofMinutes(10))
        advance(Duration.ofMinutes(2))
        stop(id) // at t0 + 16 min, on the new monotonic clock

        online = true
        advance(Duration.ofMinutes(3))
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(t0, t0 + 16 * minute, "STOPPED"), serverSession(id))
    }

    @Test
    fun `Ein Neustart, nach dem die monotone Uhr schon weiter ist als beim Start, wird trotzdem erkannt`() = scenario {
        val t0 = server.clock.now().toEpochMilli()
        online = false
        val id = start() // monotonic start value is small
        reboot(Duration.ofMinutes(10))
        advance(Duration.ofHours(1)) // the new boot has been running longer than the old one at the Start

        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(t0, serverSession(id).first)
    }

    @Test
    fun `Stoppt das Kind offline vor der Grenze und der Server stoppt automatisch, gilt nach dem Sync das frühere Ende des Kindes`() = scenario {
        val start = server.clock.now().toEpochMilli()
        val id = start()
        sync()
        online = false
        advance(Duration.ofMinutes(50))
        stop(id) // the child stops at 50 min, before the Überziehungsgrenze at 120 min
        advance(Duration.ofMinutes(80))
        server.services.scheduler.runDue() // the server knows nothing and stops automatically at 120 min
        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), serverSession(id))

        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(start, start + 50 * minute, "STOPPED"), serverSession(id))
        val stand = child.storedSnapshot()!!.toKindStand().tagesstand(server.clock.now())
        assertEquals(Duration.ofMinutes(50), stand.verbrauch)
        assertNull(stand.laufendeSitzung)
        assertTrue(child.outbox().isEmpty())
    }
}
