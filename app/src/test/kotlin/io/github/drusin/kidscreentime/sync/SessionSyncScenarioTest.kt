package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.child.toKindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ticket 05: Start and Stopp travel through the Outbox of the app to the real server (Sync-Protokoll). */
@RunWith(RobolectricTestRunner::class)
class SessionSyncScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val minute = 60_000L

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        sync() // the first sync at the start of the app brings the first snapshot
        requests.clear()
        block()
    }

    @Test
    fun `Ein Start geht zuerst in die Outbox und erst der Sync bringt ihn zum Server`() = scenario {
        val id = start()

        assertEquals(listOf(id), child.outbox().map { it.sessionId })
        assertTrue(requests.isEmpty(), "Der Start braucht kein Netz")
        assertNull(runCatching { serverSession(id) }.getOrNull(), "Der Server kennt die Sitzung noch nicht")

        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(listOf("POST ${ApiPaths.CHILD_SYNC}"), requests)
        val now = server.clock.now().toEpochMilli()
        assertEquals(Triple(now, null, null), serverSession(id))
        assertEquals(ChildState.RUNNING, serverSnapshot().status.state)
        assertEquals(id, child.storedSnapshot()?.status?.runningSession?.id, "Der Sync liefert den Snapshot mit der Sitzung")
        assertTrue(child.outbox().isEmpty(), "Zugestellt: die Outbox ist leer")
    }

    @Test
    fun `Der Start wird als Alter übertragen, auch wenn das Netz erst später da ist`() = scenario {
        online = false
        val start = server.clock.now().toEpochMilli()
        val id = start()

        advance(Duration.ofMinutes(8))
        assertEquals(ChildSyncResult.Unavailable, sync())
        assertEquals(1, child.outbox().size, "Ohne Netz bleibt der Start in der Outbox")
        online = true
        advance(Duration.ofMinutes(2))
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(start, null, null), serverSession(id), "Serverzeit beim Empfang minus Alter ist der echte Start")
        assertEquals(10 * minute, serverSnapshot().status.usedMillis)
    }

    @Test
    fun `Ein Stopp nach zugestelltem Start bringt seinen Start mit und die Restzeit bleibt`() = scenario {
        val id = start()
        sync()
        advance(Duration.ofMinutes(25))

        stop(id)
        assertEquals(ChildSyncResult.Synced, sync())

        val now = server.clock.now().toEpochMilli()
        assertEquals(Triple(now - 25 * minute, now, "STOPPED"), serverSession(id))
        val status = child.storedSnapshot()!!.status
        assertEquals(ChildState.READY, status.state)
        assertEquals(35 * minute, status.remainingMillis)
        assertTrue(child.outbox().isEmpty())
    }

    @Test
    fun `Start und Stopp ohne Netz kommen zusammen als eine Sitzung an`() = scenario {
        online = false
        val start = server.clock.now().toEpochMilli()
        val id = start()
        advance(Duration.ofMinutes(12))
        stop(id)
        advance(Duration.ofMinutes(3))
        online = true

        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(start, start + 12 * minute, "STOPPED"), serverSession(id))
        assertEquals(12 * minute, serverSnapshot().status.usedMillis)
    }

    @Test
    fun `Ein wiederholter Sync ändert nichts`() = scenario {
        val id = start()
        advance(Duration.ofMinutes(5))
        stop(id)
        sync()
        val stored = serverSession(id)

        advance(Duration.ofMinutes(30))
        assertEquals(ChildSyncResult.Synced, sync())
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(stored, serverSession(id))
        assertEquals(1, serverSnapshot().todaySessions.size)
    }

    @Test
    fun `Das frühere Ende gewinnt, ein Stopp nach dem Automatischen Stopp des Servers ändert nichts`() = scenario {
        val id = start()
        sync()
        val start = server.clock.now().toEpochMilli()
        advance(Duration.ofMinutes(100))
        // The server ended the Sitzung while the tablet was offline (the Automatischer Stopp of ticket 07).
        serverSql("UPDATE sessions SET end_at = ${start + 90 * minute}, end_reason = 'AUTOMATIC_STOP' WHERE id = '$id'")
        online = false
        stop(id)
        online = true

        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(start, start + 90 * minute, "AUTOMATIC_STOP"), serverSession(id))
        val stand = child.storedSnapshot()!!.toKindStand().tagesstand(server.clock.now())
        assertNull(stand.laufendeSitzung)
        assertEquals(Duration.ofMinutes(90), stand.verbrauch)
    }

    @Test
    fun `Der Server lehnt einen Start bei Restzeit 0 nicht ab, das Verbot prüft nur das Kindgerät`() = scenario {
        val first = start()
        advance(Duration.ofMinutes(60))
        stop(first)
        sync()
        assertEquals(Zustand.AUFGEBRAUCHT, child.storedSnapshot()!!.toKindStand().tagesstand(server.clock.now()).zustand)

        // A Start the tablet should not have made (for example a second tablet's stand was older): still a fact.
        val second = start()
        assertEquals(ChildSyncResult.Synced, sync())

        assertNotNull(serverSession(second))
        assertEquals(ChildState.OVERRUN, serverSnapshot().status.state)
    }

    @Test
    fun `Ein Stopp während eines laufenden Syncs geht nicht verloren`() = scenario {
        val id = start()
        val sent = child.outbox().single()
        advance(Duration.ofMinutes(1))

        stop(id) // pressed while the sync of the Start was in flight

        child.app.database.outboxDao().deleteDelivered(sent.sessionId, sent.revision)
        val kept = child.outbox().single()
        assertNotNull(kept.endWallMillis, "Die Zeile mit dem Stopp bleibt, denn zugestellt war nur der Start")
        assertEquals(ChildSyncResult.Synced, sync())
        assertEquals(1 * minute, serverSnapshot().status.usedMillis)
    }
}
