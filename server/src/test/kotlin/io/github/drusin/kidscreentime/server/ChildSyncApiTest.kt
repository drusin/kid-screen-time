package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/** Ticket 05: the sync of the Kindgerät upserts Sitzungen (Sync-Protokoll) and returns the complete Kind-Snapshot. */
class ChildSyncApiTest {
    @TempDir
    lateinit var dir: Path

    // Default clock: Monday 2026-01-05 09:00 in Berlin, Wochenvorlage 60 min.
    private val minute = 60_000L

    private suspend fun HttpClient.sync(device: RedeemPairingCodeResponse, vararg sessions: SessionUpdate): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(sessions.toList()))
        }

    private suspend fun HttpClient.snapshotAfter(device: RedeemPairingCodeResponse, vararg sessions: SessionUpdate): ChildSnapshot {
        val response = sync(device, *sessions)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    private fun uuid() = UUID.randomUUID().toString()

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
    fun `Ein Start legt die Sitzung an und rechnet Serverzeit beim Empfang minus Alter`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        val now = server.clock.now().toEpochMilli()

        val snapshot = client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 3 * minute))

        assertEquals(Triple(now - 3 * minute, null, null), server.storedSession(id))
        assertEquals(ChildState.RUNNING, snapshot.status.state)
        assertEquals(id, snapshot.status.runningSession?.id)
        assertEquals(now - 3 * minute, snapshot.status.runningSession?.startEpochMillis)
        assertEquals(3 * minute, snapshot.status.usedMillis)
    }

    @Test
    fun `Ein Stopp bringt seinen Start mit, auch wenn der Server die Sitzung noch nicht kennt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        val now = server.clock.now().toEpochMilli()

        val snapshot = client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 10 * minute, endAgeMillis = 4 * minute))

        assertEquals(Triple(now - 10 * minute, now - 4 * minute, "STOPPED"), server.storedSession(id))
        assertEquals(ChildState.READY, snapshot.status.state)
        assertEquals(6 * minute, snapshot.status.usedMillis)
        assertEquals(54 * minute, snapshot.status.remainingMillis)
    }

    @Test
    fun `Der Start wird später durch den Stopp ergänzt, das Alter rechnet ab dem Empfang des Stopps`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        val start = server.clock.now().toEpochMilli()
        client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 0))

        server.clock.advance(Duration.ofMinutes(20))
        client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 20 * minute, endAgeMillis = 0))

        assertEquals(Triple(start, start + 20 * minute, "STOPPED"), server.storedSession(id))
    }

    @Test
    fun `Doppelte Übertragungen sind wirkungslos`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        val first = client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 5 * minute, endAgeMillis = 2 * minute))
        val stored = server.storedSession(id)

        // The same state again, later: the ages are now bigger than they were at the first receipt.
        server.clock.advance(Duration.ofMinutes(7))
        val again = client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 12 * minute, endAgeMillis = 9 * minute))

        assertEquals(stored, server.storedSession(id))
        assertEquals(first.todaySessions, again.todaySessions)
        assertEquals(1, again.todaySessions.size)
    }

    @Test
    fun `Das frühere Ende gewinnt, ein späterer Stopp einer schon beendeten Sitzung ändert nichts`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        val now = server.clock.now().toEpochMilli()
        // The server ended the Sitzung (e.g. Automatischer Stopp, ticket 07) 30 min after its start.
        server.services.database.transaction { c ->
            c.createStatement().use {
                it.execute("INSERT INTO sessions (id, child_id, start_at, end_at, end_reason) VALUES ('$id','${child.childId}',${now - 90 * minute},${now - 60 * minute},'AUTOMATIC_STOP')")
            }
        }

        val snapshot = client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 90 * minute, endAgeMillis = 0))

        assertEquals(Triple(now - 90 * minute, now - 60 * minute, "AUTOMATIC_STOP"), server.storedSession(id))
        assertEquals(30 * minute, snapshot.status.usedMillis)
    }

    @Test
    fun `Der Server lehnt keinen Start ab, auch nicht bei Restzeit 0`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.snapshotAfter(child, SessionUpdate(uuid(), startAgeMillis = 70 * minute, endAgeMillis = 5 * minute))

        val second = uuid()
        val snapshot = client.snapshotAfter(child, SessionUpdate(second, startAgeMillis = 0))

        assertEquals(ChildState.OVERRUN, snapshot.status.state)
        assertEquals(second, snapshot.status.runningSession?.id)
        assertEquals(5 * minute, snapshot.status.overrunMillis)
    }

    @Test
    fun `Zeitpunkte liegen nie in der Zukunft und das Ende nie vor dem Start`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        val now = server.clock.now().toEpochMilli()

        client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = -5 * minute, endAgeMillis = 3 * minute))

        assertEquals(Triple(now, now, "STOPPED"), server.storedSession(id))
    }

    @Test
    fun `Mehrere Sitzungen eines Batches werden in Startreihenfolge angelegt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val early = uuid()
        val late = uuid()

        val snapshot = client.snapshotAfter(
            child,
            SessionUpdate(late, startAgeMillis = 10 * minute),
            SessionUpdate(early, startAgeMillis = 30 * minute, endAgeMillis = 20 * minute),
        )

        assertEquals(listOf(early, late), snapshot.todaySessions.map { it.id })
    }

    @Test
    fun `Eine Sitzung eines anderen Kindes bleibt unberührt und der ganze Batch wird abgelehnt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val foreign = uuid()
        val fresh = uuid()
        val now = server.clock.now().toEpochMilli()
        server.services.database.transaction { c ->
            c.createStatement().use {
                it.execute("INSERT INTO children (id, name, created_at) VALUES ('anderes-kind','Ben',$now)")
                it.execute("INSERT INTO sessions (id, child_id, start_at) VALUES ('$foreign','anderes-kind',${now - minute})")
            }
        }

        val response = client.sync(child, SessionUpdate(fresh, startAgeMillis = 0), SessionUpdate(foreign, startAgeMillis = 0, endAgeMillis = 0))

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(server.storedSession(foreign).second)
        assertEquals(0, server.services.database.transaction { c ->
            c.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM sessions WHERE id = '$fresh'").use { it.next(); it.getInt(1) } }
        })
    }

    @Test
    fun `Eine ungültige Sitzungs-ID antwortet mit 400`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)

        assertEquals(HttpStatusCode.BadRequest, client.sync(child, SessionUpdate("keine-uuid", startAgeMillis = 0)).status)
    }

    @Test
    fun `Die Sitzung überlebt einen Neustart des Servers`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = uuid()
        client.snapshotAfter(child, SessionUpdate(id, startAgeMillis = 2 * minute))
        val stored = server.storedSession(id)
        server.services.database.close()

        val restarted = TestServer(dir, server.clock)

        assertEquals(stored, restarted.storedSession(id))
        assertEquals(id, restarted.services.children.childSnapshot(child.childId!!)?.status?.runningSession?.id)
    }

    @Test
    fun `Der Sync verlangt ein Kindgerät-Token`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)

        assertEquals(HttpStatusCode.Forbidden, client.sync(parent).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
                contentType(ContentType.Application.Json)
                setBody(ChildSyncRequest())
            }.status,
        )
    }
}
