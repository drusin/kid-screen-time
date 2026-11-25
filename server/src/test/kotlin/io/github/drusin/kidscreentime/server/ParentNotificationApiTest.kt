package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Ticket 14: Eltern-Benachrichtigungen. Time moves on the `ManualClock`, `scheduler.runDue()` does what the loop does,
 * the pushes arrive at a [FakeNtfy] and are decrypted like a Elterngerät does. Default clock: Monday 2026-01-05 09:00
 * in Berlin, Wochenvorlage 60 min, Überziehungsgrenze 60 min: Restzeit 0 at 10:00, Automatischer Stopp at 11:00.
 */
class ParentNotificationApiTest {
    @TempDir
    lateinit var dir: Path

    private val ntfy = FakeNtfy()

    @AfterTest
    fun stopNtfy() = ntfy.close()

    private class Parent(val receiver: WebPushReceiver, val topic: String)

    private suspend fun TestServer.pairParent(client: HttpClient, topic: String): Parent {
        val paired = pairDevice(client, DeviceRole.PARENT, name = topic)
        val parent = Parent(WebPushReceiver(), topic)
        client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            header("Authorization", "Bearer ${paired.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(PushRegistration("https://ntfy.example.org/$topic?up=1", parent.receiver.publicKeyBase64Url, parent.receiver.authSecretBase64Url))
        }
        return parent
    }

    private suspend fun HttpClient.sync(device: RedeemPairingCodeResponse, vararg updates: SessionUpdate) =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(updates.toList()))
        }

    private suspend fun HttpClient.startSession(device: RedeemPairingCodeResponse, startAge: Duration = Duration.ZERO): String =
        UUID.randomUUID().toString().also { sync(device, SessionUpdate(it, startAgeMillis = startAge.toMillis())) }

    /** The texts one Elterngerät received so far (signals without text are plain Resync-Signale). */
    private fun TestServer.texts(parent: Parent): List<String> {
        services.push.flush()
        return ntfy.requests.filter { it.pathAndQuery == "/${parent.topic}?up=1" }
            .mapNotNull { ApiJson.decodeFromString(PushSignal.serializer(), parent.receiver.decrypt(it.body).decodeToString()).text }
    }

    private fun TestServer(clockOf: TestServer? = null) =
        TestServer(dir, clockOf?.clock ?: io.github.drusin.kidscreentime.domain.clock.ManualClock(), ntfyInternalUrl = ntfy.url)

    @Test
    fun `Bei Restzeit 0 bekommen alle Elterngeräte genau eine Benachrichtigung mit dem Kindnamen vorne`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val first = server.pairParent(client, "upELTERN0001")
        val second = server.pairParent(client, "upELTERN0002")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.startSession(child)

        server.clock.advance(Duration.ofMinutes(59))
        server.services.scheduler.runDue()
        assertEquals(emptyList(), server.texts(first), "Noch Restzeit übrig")

        server.clock.advance(Duration.ofMinutes(1))
        server.services.scheduler.runDue()
        server.clock.advance(Duration.ofMinutes(10))
        server.services.scheduler.runDue()

        val expected = listOf("Mia: Kontingent abgelaufen (seit 10:00)")
        assertEquals(expected, server.texts(first))
        assertEquals(expected, server.texts(second), "Alle Elterngeräte dieselbe Benachrichtigung")
    }

    @Test
    fun `Eine Sitzung, die der Server erst beim Sync kennt und die schon überzieht, meldet jetzt genau einmal`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)

        // Offline gestartet vor 90 min: die Restzeit war um 08:30 bei 0, die Überziehungsgrenze (60 min) ist noch nicht erreicht.
        client.startSession(child, Duration.ofMinutes(90))
        server.services.scheduler.runDue()
        server.services.scheduler.runDue()
        client.sync(child)
        server.services.scheduler.runDue()

        assertEquals(listOf("Mia: Kontingent abgelaufen (seit 08:30)"), server.texts(parent))
    }

    @Test
    fun `Für eine Sitzung, die offline schon beendet wurde, kommt keine Benachrichtigung`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)

        // Gestartet vor 150 min, offline vor 80 min gestoppt: lief über die Restzeit 0 hinaus, ist aber längst beendet.
        val id = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(id, startAgeMillis = 150 * 60_000, endAgeMillis = 80 * 60_000))
        server.services.scheduler.runDue()

        assertEquals(emptyList(), server.texts(parent))
        assertEquals(null, server.services.scheduler.nextDue())
    }

    @Test
    fun `Der Automatische Stopp meldet genau einmal mit der Überziehungsgrenze, ohne zweites Signal`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.startSession(child)

        server.clock.advance(Duration.ofMinutes(61))
        server.services.scheduler.runDue()
        server.clock.advance(Duration.ofMinutes(60))
        server.services.scheduler.runDue()
        server.services.scheduler.runDue()

        assertEquals(
            listOf("Mia: Kontingent abgelaufen (seit 10:00)", "Mia: Automatischer Stopp nach 60 min Überziehung"),
            server.texts(parent),
        )
        assertEquals(3, ntfy.requests.size, "Start des Kindes plus ein Push je Ereignis: der mit Text löst den Abgleich schon aus")
    }

    @Test
    fun `Meldet das Tablet den Automatischen Stopp vor dem Lauf des Servers, kommt genau eine Benachrichtigung`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = client.startSession(child)
        server.services.push.flush()
        val before = ntfy.requests.size

        // Das Tablet stoppt um 11:00 offline, sein Sync kommt 30 s später, vor dem Planer.
        server.clock.advance(Duration.ofMinutes(120) + Duration.ofSeconds(30))
        client.sync(child, SessionUpdate(id, startAgeMillis = Duration.ofMinutes(120).toMillis() + 30_000, endAgeMillis = 30_000, endReason = SessionEndReason.AUTOMATIC_STOP))
        client.sync(child, SessionUpdate(id, startAgeMillis = Duration.ofMinutes(120).toMillis() + 30_000, endAgeMillis = 30_000, endReason = SessionEndReason.AUTOMATIC_STOP))
        server.services.scheduler.runDue()
        server.services.scheduler.runDue()

        assertEquals(listOf("Mia: Automatischer Stopp nach 60 min Überziehung"), server.texts(parent))
        assertEquals(1, ntfy.requests.size - before, "Ein einziger Push, mit Text an das Elterngerät (es hat sich nur das Elterngerät registriert; der Text löst den Abgleich schon aus)")
    }

    @Test
    fun `Stoppen Tablet und Server fast gleichzeitig, kommt die Benachrichtigung nicht doppelt`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = client.startSession(child)

        server.clock.advance(Duration.ofMinutes(121))
        server.services.scheduler.runDue() // Der Server stoppt zuerst und meldet.
        // Das Tablet kam 5 s früher zur Grenze (klar früheres Ende, Grund wird „Automatischer Stopp“ vom Tablet) und meldet es spät.
        client.sync(child, SessionUpdate(id, startAgeMillis = Duration.ofMinutes(121).toMillis(), endAgeMillis = Duration.ofMinutes(1).toMillis() + 5_000, endReason = SessionEndReason.AUTOMATIC_STOP))

        assertEquals(listOf("Mia: Automatischer Stopp nach 60 min Überziehung"), server.texts(parent))
    }

    @Test
    fun `Ein Automatischer Stopp, der erst lange danach per Offline-Sync bekannt wird, benachrichtigt nicht nachträglich`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = client.startSession(child)

        server.clock.advance(Duration.ofMinutes(150)) // Das Tablet war 30 min offline, der Server-Planer lief nicht.
        client.sync(child, SessionUpdate(id, startAgeMillis = Duration.ofMinutes(150).toMillis(), endAgeMillis = Duration.ofMinutes(30).toMillis(), endReason = SessionEndReason.AUTOMATIC_STOP))
        server.services.scheduler.runDue()

        assertEquals(emptyList(), server.texts(parent))
    }

    @Test
    fun `Eine offline gestartete und automatisch gestoppte Sitzung, die der Server nie laufen sah, benachrichtigt nicht`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)

        val id = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(id, startAgeMillis = Duration.ofMinutes(125).toMillis(), endAgeMillis = 5_000, endReason = SessionEndReason.AUTOMATIC_STOP))

        assertEquals(emptyList(), server.texts(parent))
    }

    @Test
    fun `Ein Server, der erst nach der Grenze wieder läuft, meldet nur den Stopp, nicht auch noch den Alarm`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.startSession(child)

        server.clock.advance(Duration.ofMinutes(180))
        server.services.scheduler.runDue()

        assertEquals(listOf("Mia: Automatischer Stopp nach 60 min Überziehung"), server.texts(parent))
    }

    @Test
    fun `Nach einem Neustart kommt keine Benachrichtigung doppelt`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.startSession(child)
        server.clock.advance(Duration.ofMinutes(65))
        server.services.scheduler.runDue()
        server.services.push.flush()

        val restarted = TestServer(server)
        assertEquals(null, restarted.services.scheduler.nextDue()?.takeIf { it <= restarted.clock.now() }, "Nichts ist fällig")
        restarted.services.scheduler.runDue()
        restarted.clock.advance(Duration.ofMinutes(60))
        restarted.services.scheduler.runDue()
        val again = TestServer(restarted)
        again.services.scheduler.runDue()

        assertEquals(
            listOf("Mia: Kontingent abgelaufen (seit 10:00)", "Mia: Automatischer Stopp nach 60 min Überziehung"),
            restarted.texts(parent),
        )
    }

    @Test
    fun `Ein Neustart nach verpasster Restzeit 0 meldet sie einmal, auch über weitere Neustarts`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.startSession(child)
        // Der Server ist bei Restzeit 0 nicht gelaufen und startet 15 min danach neu.
        server.clock.advance(Duration.ofMinutes(75))

        val restarted = TestServer(server)
        restarted.services.scheduler.runDue()
        TestServer(restarted).services.scheduler.runDue()

        assertEquals(listOf("Mia: Kontingent abgelaufen (seit 10:00)"), restarted.texts(parent))
    }

    @Test
    fun `Hebt eine Tagesanpassung die Restzeit wieder über 0, meldet ein erneutes Erreichen nichts mehr (einmal je Sitzung)`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val parentDevice = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.startSession(child)
        server.clock.advance(Duration.ofMinutes(61))
        server.services.scheduler.runDue()

        client.post(TestServer.PUBLIC + ApiPaths.PARENT_DAILY_ADJUSTMENTS) {
            header("Authorization", "Bearer ${parentDevice.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(DailyAdjustmentRequest(checkNotNull(child.childId), 30))
        }
        server.clock.advance(Duration.ofMinutes(35))
        server.services.scheduler.runDue()

        assertEquals(listOf("Mia: Kontingent abgelaufen (seit 10:00)"), server.texts(parent))
    }

    @Test
    fun `Eine neue Sitzung nach dem Alarm bekommt wieder ihre eigene Benachrichtigung`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val first = client.startSession(child)
        server.clock.advance(Duration.ofMinutes(61))
        server.services.scheduler.runDue()
        client.sync(child, SessionUpdate(first, startAgeMillis = 61 * 60_000, endAgeMillis = 0))
        client.startSession(child)
        server.services.scheduler.runDue()

        assertEquals(2, server.texts(parent).size)
        assertTrue(server.texts(parent).all { it.startsWith("Mia: Kontingent abgelaufen") })
    }
}
