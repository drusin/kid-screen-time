package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.child.toKindStand
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ticket 07: the Automatischer Stopp happens at the server and at the Kindgerät, each on its own and also offline,
 * and both agree (the earlier end wins, so there is no conflict). The server's part is `scheduler.runDue()` after
 * moving the clock; the Kindgerät's part is the trigger of its Alarmkern.
 */
@RunWith(RobolectricTestRunner::class)
class AutomaticStopScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val minute = 60_000L

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        sync()
        block()
    }

    @Test
    fun `Ein späterer Stopp des Kindes nach dem Automatischen Stopp des Servers ändert nichts`() = scenario {
        val start = server.clock.now().toEpochMilli()
        val id = start()
        sync()
        online = false
        advance(Duration.ofMinutes(130))
        server.services.scheduler.runDue() // the server stops at 120 min, the tablet knows nothing

        stop(id) // the child presses Stopp at 130 min, offline
        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), serverSession(id))
        val stand = child.storedSnapshot()!!.toKindStand().tagesstand(server.clock.now())
        assertNull(stand.laufendeSitzung)
        assertEquals(Duration.ofMinutes(120), stand.verbrauch, "Das Kind zahlt nicht für die Zeit nach der Grenze")
        assertTrue(child.outbox().isEmpty())
    }

    @Test
    fun `Server und Tablet stoppen beide offline zum selben Zeitpunkt, der Sync ändert nichts mehr`() = scenario {
        val start = server.clock.now().toEpochMilli()
        val id = start()
        sync()
        online = false
        advance(Duration.ofMinutes(125))

        server.services.scheduler.runDue()
        child.app.container.alarmKern.trigger() // local Automatischer Stopp (the sync inside fails: offline)
        assertEquals(1, child.outbox().size, "Der lokale Stopp wartet in der Outbox")
        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), serverSession(id))
        assertTrue(child.outbox().isEmpty())
        assertNull(child.storedSnapshot()!!.toKindStand().tagesstand(server.clock.now()).laufendeSitzung)
    }

    @Test
    fun `Der lokale Automatische Stopp des Tablets bleibt für den Verlauf markiert, auch wenn sein Sync vor dem Lauf des Servers kommt`() = scenario {
        val start = server.clock.now().toEpochMilli()
        val id = start()
        sync()
        online = false
        advance(Duration.ofMinutes(125))

        child.app.container.alarmKern.trigger() // local stop at 120 min, the sync inside fails: offline
        online = true
        assertEquals(ChildSyncResult.Synced, sync()) // arrives before the server ran its own Automatischer Stopp

        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), serverSession(id), "nicht „STOPPED“")
        assertEquals(0, server.services.scheduler.runDue(), "der Server hat nichts mehr zu stoppen")
    }

    @Test
    fun `Das Tablet stoppt lokal, auch wenn der Server nichts davon weiß, und das Ende stimmt mit der Grenze überein`() = scenario {
        val start = server.clock.now().toEpochMilli()
        val id = start()
        sync()
        online = false
        advance(Duration.ofMinutes(121))

        child.app.container.alarmKern.trigger()
        val local = child.app.container.childRepository.local.first()!!

        assertNull(local.stored.snapshot.toKindStand(local.pending).tagesstand(server.clock.now()).laufendeSitzung)
        assertEquals(start + 120 * minute, local.pending.single { it.id == id }.endWallMillis)
    }
}
