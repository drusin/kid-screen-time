package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.api.RedeemPairingCodeRequest
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Ticket 13: Resync-Signale über ntfy. Statt ntfy antwortet ein echter HTTP-Endpunkt ([FakeNtfy]); geprüft wird, dass
 * die richtige verschlüsselte Nachricht am richtigen Topic ankommt und nur das Zielgerät sie entschlüsseln kann.
 */
class PushApiTest {
    @TempDir
    lateinit var dir: Path

    private val ntfy = FakeNtfy()

    @AfterTest
    fun stopNtfy() = ntfy.close()

    private fun endpoint(topic: String) = "https://ntfy.example.org/$topic?up=1"

    /** A device with its own WebPush keys, registered on [topic]. */
    private class Device(val token: RedeemPairingCodeResponse, val receiver: WebPushReceiver, val topic: String) {
        val registration = PushRegistration("https://ntfy.example.org/$topic?up=1", receiver.publicKeyBase64Url, receiver.authSecretBase64Url)
    }

    private suspend fun HttpClient.register(token: String?, registration: PushRegistration): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(registration)
        }

    private suspend fun TestServer.pairWithPush(client: HttpClient, role: DeviceRole, topic: String): Device {
        val paired = pairDevice(client, role, name = topic)
        val device = Device(paired, WebPushReceiver(), topic)
        assertEquals(HttpStatusCode.NoContent, client.register(paired.deviceToken, device.registration).status)
        return device
    }

    private suspend fun HttpClient.sync(device: Device, vararg sessions: SessionUpdate) =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.token.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(sessions.toList()))
        }

    private fun NtfyRequest.decryptFor(device: Device): PushSignal =
        ApiJson.decodeFromString(PushSignal.serializer(), device.receiver.decrypt(body).decodeToString())

    @Test
    fun `ein Start des Kindes geht verschlüsselt ans Topic des Elterngeräts, nicht ans Kindgerät`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        val child = server.pairWithPush(client, DeviceRole.CHILD, "upKINDtopic0001")

        client.sync(child, SessionUpdate(UUID.randomUUID().toString(), startAgeMillis = 0))
        server.services.push.flush()

        val request = ntfy.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/upELTERNtopic1?up=1", request.pathAndQuery)
        assertEquals("aes128gcm", request.headers["content-encoding"])
        assertEquals(PushSignal(), request.decryptFor(parent))
    }

    @Test
    fun `der Body am Topic ist reiner Chiffretext und nur das Zielgerät kann ihn lesen`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        val other = server.pairWithPush(client, DeviceRole.PARENT, "upEltern2topic")
        val child = server.pairWithPush(client, DeviceRole.CHILD, "upKINDtopic0001")

        server.services.push.signalParents(PushSignal(title = "Mia", text = "Alarm: Zeit ist um"))
        server.services.push.flush()

        assertEquals(setOf("/upELTERNtopic1?up=1", "/upEltern2topic?up=1"), ntfy.requests.map { it.pathAndQuery }.toSet())
        for (request in ntfy.requests) {
            val target = if (request.pathAndQuery.contains("topic1")) parent else other
            val foreign = if (target === parent) other else child
            assertEquals(PushSignal("Mia", "Alarm: Zeit ist um"), request.decryptFor(target))
            assertTrue(request.body.decodeToString().let { "Mia" !in it && "Alarm" !in it }, "Klartext im Body")
            assertTrue(runCatching { foreign.receiver.decrypt(request.body) }.isFailure, "Fremdes Gerät konnte lesen")
        }
    }

    @Test
    fun `ein Signal ans Kind geht nur an das Topic seines Kindgeräts`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        val child = server.pairWithPush(client, DeviceRole.CHILD, "upKINDtopic0001")
        val childId = checkNotNull(child.token.childId)

        server.services.push.signalChild(childId)
        server.services.push.flush()

        assertEquals("/upKINDtopic0001?up=1", ntfy.requests.single().pathAndQuery)
        assertEquals(PushSignal(), ntfy.requests.single().decryptFor(child))
    }

    @Test
    fun `ein Sync ohne neuen Start oder Stopp löst kein Signal aus`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        val child = server.pairWithPush(client, DeviceRole.CHILD, "upKINDtopic0001")
        val id = UUID.randomUUID().toString()

        client.sync(child, SessionUpdate(id, startAgeMillis = 0))
        server.services.push.flush()
        assertEquals(1, ntfy.requests.size)

        // The same Outbox again (a retry) and an empty sync change nothing.
        client.sync(child, SessionUpdate(id, startAgeMillis = 0))
        client.sync(child)
        server.services.push.flush()
        assertEquals(1, ntfy.requests.size)

        // The Stopp is new.
        client.sync(child, SessionUpdate(id, startAgeMillis = 0, endAgeMillis = 0))
        server.services.push.flush()
        assertEquals(2, ntfy.requests.size)
    }

    @Test
    fun `entkoppelte und unregistrierte Geräte bekommen nichts`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        server.pairDevice(client, DeviceRole.PARENT) // no registration
        val registered = server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        server.services.database.transaction { c ->
            c.createStatement().use { it.executeUpdate("UPDATE devices SET unpaired_at = 1 WHERE id = '${registered.token.deviceId}'") }
        }

        server.services.push.signalParents()
        server.services.push.flush()

        assertTrue(ntfy.requests.isEmpty())
    }

    @Test
    fun `die Registrierung kann sich ändern und das neue Topic gilt sofort`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        val moved = WebPushReceiver()
        val registration = PushRegistration(endpoint("upNEUEStopic001"), moved.publicKeyBase64Url, moved.authSecretBase64Url)

        assertEquals(HttpStatusCode.NoContent, client.register(parent.token.deviceToken, registration).status)
        server.services.push.signalParents()
        server.services.push.flush()

        val request = ntfy.requests.single()
        assertEquals("/upNEUEStopic001?up=1", request.pathAndQuery)
        assertContentEquals(
            ApiJson.encodeToString(PushSignal.serializer(), PushSignal()).toByteArray(),
            moved.decrypt(request.body),
        )
    }

    @Test
    fun `der Server veröffentlicht nur an Topics des eigenen ntfy und lehnt fremde Endpoints ab`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val receiver = WebPushReceiver()
        fun reg(endpoint: String) = PushRegistration(endpoint, receiver.publicKeyBase64Url, receiver.authSecretBase64Url)

        for (bad in listOf(
            "https://evil.example.com/upABCDEFGH?up=1",
            "https://ntfy.example.org.evil.com/upABCDEFGH",
            "https://ntfy.example.org/../admin",
            "https://ntfy.example.org/notup1234",
            "https://ntfy.example.org/upABCD/extra",
            "http://127.0.0.1:1/upABCDEFGH",
        )) {
            assertEquals(HttpStatusCode.BadRequest, client.register(parent.deviceToken, reg(bad)).status, bad)
        }
        assertEquals(
            HttpStatusCode.BadRequest,
            client.register(parent.deviceToken, PushRegistration(endpoint("upABCDEFGH"), "AAAA", receiver.authSecretBase64Url)).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.register(parent.deviceToken, PushRegistration(endpoint("upABCDEFGH"), receiver.publicKeyBase64Url, "AAAA")).status,
        )
        server.services.push.signalParents()
        server.services.push.flush()
        assertTrue(ntfy.requests.isEmpty())
    }

    @Test
    fun `die Push-Registrierung braucht ein gültiges Token, beide Rollen dürfen sie aktualisieren`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val receiver = WebPushReceiver()
        val registration = PushRegistration(endpoint("upABCDEFGH"), receiver.publicKeyBase64Url, receiver.authSecretBase64Url)

        assertEquals(HttpStatusCode.Unauthorized, client.register(null, registration).status)
        assertEquals(HttpStatusCode.Unauthorized, client.register("falsches-token", registration).status)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        assertEquals(HttpStatusCode.NoContent, client.register(parent.deviceToken, registration).status)
        assertEquals(HttpStatusCode.NoContent, client.register(child.deviceToken, registration).status)

        // After the Entkoppeln the token is invalid.
        server.services.database.transaction { c ->
            c.createStatement().use { it.executeUpdate("UPDATE devices SET unpaired_at = 1 WHERE id = '${child.deviceId}'") }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.register(child.deviceToken, registration).status)
    }

    @Test
    fun `die Push-Registrierung kann schon beim Koppeln mitgeschickt werden`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val receiver = WebPushReceiver()
        val code = server.services.pairing.createCode(DeviceRole.PARENT).code

        val response = client.post(TestServer.PUBLIC + ApiPaths.PAIRING_REDEEM) {
            contentType(ContentType.Application.Json)
            setBody(
                RedeemPairingCodeRequest(
                    code, "Handy",
                    PushRegistration(endpoint("upBEIMKOPPELN1"), receiver.publicKeyBase64Url, receiver.authSecretBase64Url),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        server.services.push.signalParents()
        server.services.push.flush()

        assertEquals("/upBEIMKOPPELN1?up=1", ntfy.requests.single().pathAndQuery)

        // A foreign endpoint at the Kopplung is refused and the code stays usable.
        val code2 = server.services.pairing.createCode(DeviceRole.PARENT).code
        val bad = client.post(TestServer.PUBLIC + ApiPaths.PAIRING_REDEEM) {
            contentType(ContentType.Application.Json)
            setBody(RedeemPairingCodeRequest(code2, "Handy", PushRegistration("https://evil.example.com/upXXXXXXXX", receiver.publicKeyBase64Url, receiver.authSecretBase64Url)))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(HttpStatusCode.OK, client.post(TestServer.PUBLIC + ApiPaths.PAIRING_REDEEM) {
            contentType(ContentType.Application.Json)
            setBody(RedeemPairingCodeRequest(code2, "Handy"))
        }.status)
    }

    @Test
    fun `ist das Topic weg (410), entfernt der Server die Registrierung und versucht es nicht wieder`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        ntfy.status = 410

        server.services.push.signalParents()
        server.services.push.flush()
        server.services.push.signalParents()
        server.services.push.flush()

        assertEquals(1, ntfy.requests.size)
        val endpoint = server.services.database.transaction { c ->
            c.createStatement().use { st -> st.executeQuery("SELECT push_endpoint FROM devices").use { it.next(); it.getString(1) } }
        }
        assertNull(endpoint)
    }

    @Test
    fun `ist ntfy nicht erreichbar, bleibt die Anfrage der Eltern-Aktion unberührt`() = testApplication {
        ntfy.close()
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        server.pairWithPush(client, DeviceRole.PARENT, "upELTERNtopic1")
        val child = server.pairWithPush(client, DeviceRole.CHILD, "upKINDtopic0001")

        val response = client.sync(child, SessionUpdate(UUID.randomUUID().toString(), startAgeMillis = 0))
        server.services.push.flush()

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
