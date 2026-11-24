package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.alarm.AlarmEffects
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.db.OutboxSessionEntity
import io.github.drusin.kidscreentime.server.TestServer
import io.github.drusin.kidscreentime.ui.TestContainer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/**
 * Reusable harness for sync scenario tests (Sync-Protokoll, tickets 05, 07, 08, 12, 13): the real app code (Room
 * Outbox, `ChildRepository`, HTTP client with its JSON) talks over a real HTTP round trip to the real Ktor server
 * (`TestServer`: real SQLite file, real routes) in the test host. Only the network cable is replaced: [online] = false
 * makes every request fail like a missing network.
 *
 * One [ManualClock][io.github.drusin.kidscreentime.domain.clock.ManualClock] drives server, wall clock and monotonic
 * clock of the app. [advance] moves time for all of them; nothing waits. Use it from a Robolectric (JUnit 4) test:
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * class MyScenarioTest {
 *     @get:Rule val folder = TemporaryFolder()
 *     @Test fun `...`() = syncScenario(folder.newFolder().toPath()) {
 *         pairChildDevice()
 *         val id = start()          // Outbox first, nothing sent yet
 *         assertEquals(ChildSyncResult.Synced, sync())
 *         assertEquals(id, serverSnapshot().status.runningSession?.id)
 *     }
 * }
 * ```
 * A second device (replaced Kindgerät, ticket 12) is a second [SyncScenario.device] call. Add what a scenario needs here instead of building a second harness.
 */
fun syncScenario(dir: Path, block: suspend SyncScenario.() -> Unit) = testApplication {
    val server = TestServer(dir).also { it.install(this) }
    // Real time on Dispatchers.Default: the test host would otherwise skip delays, but Room and the HTTP round trip run on real threads.
    withContext(Dispatchers.Default) { SyncScenario(server, this@testApplication).block() }
}


class SyncScenario(
    server: TestServer,
    private val host: ApplicationTestBuilder,
) {
    val server: TestServer = server

    /** `false`: every request of the app fails with an `IOException` (no network). */
    var online: Boolean = true

    /** Request bodies and paths the app sent, in order (also the failed ones). */
    val requests = mutableListOf<String>()

    private val relay: HttpClient = host.createClient { }

    /** One simulated device: its own container (Room, token) and, for a Kindgerät, the Kind it is paired to. */
    inner class Device(val app: TestContainer) {
        val repository get() = app.container.childRepository

        /** `true`: only this device has no network (the others are not affected, unlike [online]). */
        @Volatile
        var offline: Boolean = false
        var childId: String? = null
            internal set
    }

    private val devices = mutableListOf<Device>()

    /** The Kindgerät most recently created by [device] / [pairChildDevice]. */
    val child: Device get() = devices.last()

    /** Creates another device of the app on the same server and clock (not paired yet). */
    fun device(alarmEffects: AlarmEffects = AlarmEffects.NONE, syncRetry: SyncRetry? = null): Device {
        lateinit var self: Device
        val engine = MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            if (!online || self.offline) throw IOException("kein Netz")
            val bytes = when (val body = request.body) {
                is OutgoingContent.ByteArrayContent -> body.bytes()
                is OutgoingContent.NoContent -> ByteArray(0)
                else -> error("Unerwarteter Body ${body::class}")
            }
            val answer = relay.request(request.url) {
                method = request.method
                request.headers.forEach { name, values -> values.forEach { headers.append(name, it) } }
                if (bytes.isNotEmpty()) setBody(ByteArrayContent(bytes, request.body.contentType))
            }
            respond(answer.readRawBytes(), answer.status, headersOf(HttpHeaders.ContentType, answer.headers[HttpHeaders.ContentType] ?: "application/json"))
        }
        return Device(TestContainer(clock = server.clock, engine = engine, alarmEffects = alarmEffects, syncRetry = syncRetry)).also { self = it; devices += it }
    }

    /** Creates a device and pairs it as the Kindgerät of a new Kind "Mia" (or of [childId] when given). */
    suspend fun pairChildDevice(childId: String? = null, alarmEffects: AlarmEffects = AlarmEffects.NONE, syncRetry: SyncRetry? = null): Device {
        val device = device(alarmEffects, syncRetry)
        val code = if (childId == null) server.services.pairing.createCode(DeviceRole.CHILD, newChildName = "Mia")
        else server.services.pairing.createCode(DeviceRole.CHILD, childId = childId)
        device.app.container.pairingRepository.pair(TestServer.PUBLIC, code.code, "Tablet")
        device.childId = (device.app.container.pairingRepository.state.value as io.github.drusin.kidscreentime.pairing.DeviceState.Paired).device.childId
        return device
    }

    /** Creates a device and pairs it as a Elterngerät (it does not count as [child]). */
    suspend fun pairParentDevice(): Device {
        val before = devices.toList()
        val device = device()
        devices.clear()
        devices += before
        val code = server.services.pairing.createCode(DeviceRole.PARENT)
        device.app.container.pairingRepository.pair(TestServer.PUBLIC, code.code, "Handy")
        return device
    }

    /** The device restarts: wall clock runs on, the monotonic clock of every device starts at zero again. */
    fun reboot(downtime: Duration) = server.clock.reboot(downtime)

    /** Time passes for server and app alike. */
    fun advance(duration: Duration) = server.clock.advance(duration)

    /** The Kindgerät presses Start: Outbox only, no network. Returns the Sitzungs-UUID. */
    suspend fun Device.start(): String = repository.startSession()

    suspend fun Device.stop(sessionId: String) = repository.stopSession(sessionId)

    suspend fun Device.sync(): ChildSyncResult = repository.sync()

    /** What the Outbox of [this] device still holds. */
    suspend fun Device.outbox(): List<OutboxSessionEntity> = app.database.outboxDao().all()

    /** What the Kindgerät calculates with: the stored snapshot, null before the first sync. */
    suspend fun Device.storedSnapshot(): ChildSnapshot? = repository.stored.first()?.snapshot

    // Shortcuts for the common one-device scenario.
    suspend fun start(): String = child.start()

    suspend fun stop(sessionId: String) = child.stop(sessionId)

    suspend fun sync(): ChildSyncResult = child.sync()

    /** The server's own view of the Kind of [child], as the sync would deliver it. */
    fun serverSnapshot(): ChildSnapshot =
        checkNotNull(server.services.children.childSnapshot(checkNotNull(child.childId))) { "Kind fehlt" }

    /** Whether the server marked the Sitzung "offline erfasst". */
    fun serverRecordedOffline(id: String): Boolean = serverSnapshot().todaySessions.single { it.id == id }.recordedOffline

    /** A row of the server's `sessions` table: start, end, end reason. */
    fun serverSession(id: String): Triple<Long, Long?, String?> = server.services.database.transaction { c ->
        c.prepareStatement("SELECT start_at, end_at, end_reason FROM sessions WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs ->
                check(rs.next()) { "Sitzung $id fehlt am Server" }
                Triple(rs.getLong(1), rs.getLong(2).takeUnless { rs.wasNull() }, rs.getString(3))
            }
        }
    }

    /** Runs [sql] directly against the server's database, to set up what only the server does (ticket 07). */
    fun serverSql(vararg sql: String) = server.services.database.transaction { c -> c.createStatement().use { st -> sql.forEach(st::execute) } }
}
