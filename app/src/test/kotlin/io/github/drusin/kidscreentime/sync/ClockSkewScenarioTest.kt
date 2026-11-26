package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.child.toKindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The clocks of tablet and server never agree exactly. The snapshot carries server times; the tablet calculates with its
 * own wall clock. Here the server's clock runs ahead: Start and Stopp must not flicker back after the sync, and the
 * local Automatischer Stopp must end exactly at the Überziehungsgrenze.
 */
@RunWith(RobolectricTestRunner::class)
class ClockSkewScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val serverAhead = Duration.ofSeconds(2)

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice(behindServer = serverAhead)
        sync()
        block()
    }

    /** What the tablet shows right now: snapshot plus Outbox, calculated with the tablet's own clock. */
    private suspend fun SyncScenario.tabletStand(): Tagesstand =
        child.repository.local.first()!!.toKindStand().tagesstand(child.app.container.wallClock.now())

    @Test
    fun `Eine gestartete Sitzung läuft am Tablet nach dem Sync weiter, auch wenn die Serveruhr vorgeht`() = scenario {
        val id = start()
        assertEquals(id, tabletStand().laufendeSitzung?.id)

        assertEquals(ChildSyncResult.Synced, sync())

        assertEquals(id, tabletStand().laufendeSitzung?.id, "Nach dem Sync springt der Knopf nicht auf Start zurück")
        advance(Duration.ofMinutes(10))
        assertEquals(Duration.ofMinutes(10), tabletStand().verbrauch)
    }

    @Test
    fun `Eine gestoppte Sitzung bleibt am Tablet nach dem Sync gestoppt, auch wenn die Serveruhr vorgeht`() = scenario {
        val id = start()
        sync()
        advance(Duration.ofMinutes(10))
        stop(id)
        assertNull(tabletStand().laufendeSitzung)

        assertEquals(ChildSyncResult.Synced, sync())

        assertNull(tabletStand().laufendeSitzung, "Nach dem Sync springt der Knopf nicht auf Stopp zurück")
        assertEquals(Duration.ofMinutes(10), tabletStand().verbrauch)
    }

    @Test
    fun `Ein Stopp offline zählt am Tablet die volle Dauer seit dem Start, auch wenn die Serveruhr vorgeht`() = scenario {
        val id = start()
        sync()
        advance(Duration.ofMinutes(10))
        online = false

        stop(id) // the Outbox holds only the end so far: the start comes from the snapshot

        assertEquals(Duration.ofMinutes(10), tabletStand().verbrauch)
        online = true
        assertEquals(ChildSyncResult.Synced, sync())
        val (start, end, _) = serverSession(id)
        assertEquals(Duration.ofMinutes(10).toMillis(), end!! - start)
    }

    @Test
    fun `Der lokale Automatische Stopp endet genau an der Überziehungsgrenze, auch wenn die Serveruhr vorgeht`() = scenario {
        val id = start()
        sync()
        online = false
        advance(Duration.ofMinutes(125))

        child.app.container.alarmKern.trigger() // local Automatischer Stopp, the sync inside fails: offline
        online = true
        assertEquals(ChildSyncResult.Synced, sync())

        val (start, end, reason) = serverSession(id)
        assertEquals(Duration.ofMinutes(120).toMillis(), end!! - start, "Das Kind bekommt keine Sekunden geschenkt")
        assertEquals("AUTOMATIC_STOP", reason)
    }
}
