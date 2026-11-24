package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir

/**
 * Ticket 07: the Automatischer Stopp at the server. Time is moved on the `ManualClock`, then `scheduler.runDue()` does
 * what the loop of the real server does. Real SQLite file; a restart is a second [TestServer] on the same folder.
 */
class AutomaticStopApiTest {
    @TempDir
    lateinit var dir: Path

    // Default clock: Monday 2026-01-05 09:00 in Berlin; Wochenvorlage 60 min, Überziehungsgrenze 60 min.
    private val minute = 60_000L

    private suspend fun HttpClient.startSession(device: RedeemPairingCodeResponse, startAge: Duration = Duration.ZERO): String {
        val id = UUID.randomUUID().toString()
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(listOf(SessionUpdate(id, startAgeMillis = startAge.toMillis()))))
        }
        return id
    }

    private suspend fun HttpClient.sync(device: RedeemPairingCodeResponse, vararg updates: SessionUpdate): ChildSnapshot =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(updates.toList()))
        }.body()

    private fun TestServer.storedSession(id: String): Triple<Long, Long?, String?> = services.database.transaction { c ->
        c.prepareStatement("SELECT start_at, end_at, end_reason FROM sessions WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs ->
                check(rs.next()) { "Sitzung $id fehlt" }
                Triple(rs.getLong(1), rs.getLong(2).takeUnless { rs.wasNull() }, rs.getString(3))
            }
        }
    }

    @Test
    fun `Der Server beendet die Sitzung zum Zeitpunkt der Grenze, auch wenn danach viel Zeit vergeht`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now().toEpochMilli()
        val id = client.startSession(child)

        // 60 min Kontingent + 60 min Überziehungsgrenze = 120 min; the server only gets to run at 125 min.
        server.clock.advance(Duration.ofMinutes(125))
        assertEquals(1, server.services.scheduler.runDue())

        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), server.storedSession(id), "Ende = Zeitpunkt der Grenze, nicht jetzt")
        val snapshot = client.sync(child)
        assertEquals(ChildState.EXHAUSTED, snapshot.status.state)
        assertNull(snapshot.status.runningSession)
        assertEquals(SessionEndReason.AUTOMATIC_STOP, snapshot.todaySessions.single().endReason)
        assertEquals(120 * minute, snapshot.status.usedMillis)
    }

    @Test
    fun `Vor der Grenze und ohne laufende Sitzung geschieht nichts`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        assertNull(server.services.scheduler.nextDue(), "Ohne Sitzung steht nichts an")
        val id = client.startSession(child)

        server.clock.advance(Duration.ofMinutes(119))

        assertEquals(0, server.services.scheduler.runDue())
        assertNull(server.storedSession(id).second)
    }

    @Test
    fun `Der Server kennt den nächsten Zeitpunkt aus der Datenbank`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now()
        client.startSession(child)

        assertEquals(start.plus(Duration.ofMinutes(120)), server.services.scheduler.nextDue())
    }

    @Test
    fun `Ein Neustart des Servers holt einen verpassten Automatischen Stopp nach`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now().toEpochMilli()
        val id = client.startSession(child)
        server.clock.advance(Duration.ofMinutes(90))

        // Restart: a new process on the same database file and the same time. Nothing was stored about plans.
        val restarted = TestServer(dir, server.clock)
        assertEquals(java.time.Instant.ofEpochMilli(start + 120 * minute), restarted.services.scheduler.nextDue())
        server.clock.advance(Duration.ofMinutes(40))
        assertEquals(1, restarted.services.scheduler.runDue())

        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), restarted.storedSession(id))
        assertEquals(0, restarted.services.scheduler.runDue(), "Ein zweiter Lauf ändert nichts")
        assertNull(restarted.services.scheduler.nextDue())
    }

    @Test
    fun `Eine Sitzung, die der Server erst spät erfährt und die schon über der Grenze liegt, endet an der Grenze`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val now = server.clock.now().toEpochMilli()

        // The tablet was offline and tells at 09:00 about a Sitzung that started 150 min ago (06:30).
        val id = client.startSession(child, startAge = Duration.ofMinutes(150))
        server.services.scheduler.runDue()

        assertEquals(Triple(now - 150 * minute, now - 30 * minute, "AUTOMATIC_STOP"), server.storedSession(id))
    }

    @Test
    fun `Die Überziehungsgrenze der Eltern bestimmt den Zeitpunkt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now().toEpochMilli()
        val id = client.startSession(child)
        server.services.database.transaction { c -> c.createStatement().use { it.execute("UPDATE children SET overrun_limit_minutes = 15") } }

        server.clock.advance(Duration.ofMinutes(80))
        server.services.scheduler.runDue()

        assertEquals(Triple(start, start + 75 * minute, "AUTOMATIC_STOP"), server.storedSession(id))
    }

    @Test
    fun `Ein Stopp des Kindes vor der Grenze verhindert den Automatischen Stopp`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now().toEpochMilli()
        val id = client.startSession(child)

        server.clock.advance(Duration.ofMinutes(30))
        client.sync(child, SessionUpdate(id, startAgeMillis = 30 * minute, endAgeMillis = 0))
        server.clock.advance(Duration.ofMinutes(200))

        assertEquals(0, server.services.scheduler.runDue())
        assertEquals(Triple(start, start + 30 * minute, "STOPPED"), server.storedSession(id))
    }

    @Test
    fun `Ein Stopp des Kindes nach dem Automatischen Stopp ändert nichts`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now().toEpochMilli()
        val id = client.startSession(child)
        server.clock.advance(Duration.ofMinutes(130))
        server.services.scheduler.runDue()

        client.sync(child, SessionUpdate(id, startAgeMillis = 130 * minute, endAgeMillis = 0))

        assertEquals(Triple(start, start + 120 * minute, "AUTOMATIC_STOP"), server.storedSession(id))
    }

    @Test
    fun `Listener erfahren vom Automatischen Stopp, der Haken für Resync und Benachrichtigung`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val start = server.clock.now()
        val id = client.startSession(child)
        val heard = mutableListOf<AutomaticStop>()
        server.services.automaticStops.onStopped { error("ein kaputter Listener hält die anderen nicht auf") }
        server.services.automaticStops.onStopped { heard += it }

        server.clock.advance(Duration.ofMinutes(121))
        server.services.scheduler.runDue()
        server.services.scheduler.runDue()

        assertEquals(listOf(AutomaticStop(checkNotNull(child.childId), id, start.plus(Duration.ofMinutes(120)))), heard, "Genau einmal")
    }

    @Test
    fun `Die Schleife des laufenden Servers stoppt von selbst, sobald sich der Stand ändert und die Zeit reicht`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = client.startSession(child)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { server.services.scheduler.run() }

        server.clock.advance(Duration.ofMinutes(130))
        // Any change of the stand wakes the loop; the sync of the Kindgerät is one.
        client.sync(child)
        val ended = withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (server.storedSession(id).second == null) delay(20)
                true
            }
        }
        scope.cancel()

        assertEquals("AUTOMATIC_STOP", server.storedSession(id).third)
        assertEquals(true, ended)
    }
}
