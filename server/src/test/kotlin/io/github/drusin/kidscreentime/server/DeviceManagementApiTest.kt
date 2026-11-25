package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.api.UnpairDeviceRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** Ticket 12: Geräteliste, Entkoppeln (letztes Elterngerät, `401` danach) und Ersetzen des Kindgeräts. */
class DeviceManagementApiTest {
    @TempDir
    lateinit var dir: Path

    private val ntfy = FakeNtfy()

    @AfterTest
    fun stopNtfy() = ntfy.close()

    private suspend fun HttpClient.parentSnapshot(parent: RedeemPairingCodeResponse): ParentSnapshot =
        get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.deviceToken}") }.body()

    private suspend fun HttpClient.unpair(by: String?, deviceId: String): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_UNPAIR_DEVICE) {
            by?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(UnpairDeviceRequest(deviceId))
        }

    private suspend fun HttpClient.sync(token: String, vararg sessions: SessionUpdate): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(sessions.toList()))
        }

    private suspend fun HttpClient.createCode(parent: RedeemPairingCodeResponse, request: CreatePairingCodeRequest): CreatePairingCodeResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_PAIRING_CODES) {
            header("Authorization", "Bearer ${parent.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    private suspend fun TestServer.pairChild(client: HttpClient, parent: RedeemPairingCodeResponse, name: String = "Tablet"): RedeemPairingCodeResponse {
        val created = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, newChildName = "Mia"))
        return client.redeem(created.code, name).body()
    }

    @Test
    fun `Die Geräteliste im Eltern-Snapshot zeigt Name, Rolle, Kopplungsdatum und zuletzt gesehen aus den Syncs`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT, "Handy")
        val pairedAt = server.clock.now().toEpochMilli()
        val child = server.pairChild(client, parent)

        server.clock.advance(Duration.ofMinutes(5))
        client.sync(child.deviceToken)

        val devices = client.parentSnapshot(parent).devices
        val tablet = devices.single { it.role == DeviceRole.CHILD }
        assertEquals("Tablet", tablet.name)
        assertEquals(pairedAt, tablet.pairedAtEpochMillis)
        assertEquals(server.clock.now().toEpochMilli(), tablet.lastSeenEpochMillis)
        assertEquals(setOf("Handy", "Tablet"), devices.map { it.name }.toSet())
    }

    @Test
    fun `Das letzte Elterngerät kann nicht entkoppelt werden und bleibt in der Liste`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT, "Handy")

        assertEquals(HttpStatusCode.Conflict, client.unpair(parent.deviceToken, parent.deviceId).status)

        assertEquals(listOf("Handy"), client.parentSnapshot(parent).devices.map { it.name })
    }

    @Test
    fun `Ein Kindgerät zählt nicht als Elterngerät, das einzige Elterngerät bleibt auch mit Kindgerät geschützt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        server.pairChild(client, parent)

        assertEquals(HttpStatusCode.Conflict, client.unpair(parent.deviceToken, parent.deviceId).status)
    }

    @Test
    fun `Ein Elterngerät entkoppelt das andere und danach das eigene nicht mehr, entkoppelt bekommt es 401`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val mama = server.pairDevice(client, DeviceRole.PARENT, "Handy Mama")
        val papa = server.pairDevice(client, DeviceRole.PARENT, "Handy Papa")

        val response = client.unpair(mama.deviceToken, papa.deviceId)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("Handy Mama"), response.body<ParentSnapshot>().devices.map { it.name })
        assertEquals(HttpStatusCode.Unauthorized, client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${papa.deviceToken}") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.unpair(papa.deviceToken, mama.deviceId).status)
        assertEquals(HttpStatusCode.Conflict, client.unpair(mama.deviceToken, mama.deviceId).status, "Mama ist jetzt das letzte")
    }

    @Test
    fun `Ein Elterngerät darf sich selbst entkoppeln, wenn ein weiteres bleibt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val mama = server.pairDevice(client, DeviceRole.PARENT, "Handy Mama")
        val papa = server.pairDevice(client, DeviceRole.PARENT, "Handy Papa")

        val response = client.unpair(mama.deviceToken, mama.deviceId)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${mama.deviceToken}") }.status)
        assertEquals(listOf("Handy Papa"), client.parentSnapshot(papa).devices.map { it.name })
    }

    @Test
    fun `Ein entkoppeltes Kindgerät bekommt beim Sync 401`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChild(client, parent)
        assertEquals(HttpStatusCode.OK, client.sync(child.deviceToken).status)

        assertEquals(HttpStatusCode.OK, client.unpair(parent.deviceToken, child.deviceId).status)

        assertEquals(HttpStatusCode.Unauthorized, client.sync(child.deviceToken).status)
    }

    @Test
    fun `Entkoppeln braucht ein Elterngerät, ein bekanntes Gerät und Token`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChild(client, parent)

        assertEquals(HttpStatusCode.Unauthorized, client.unpair(null, child.deviceId).status)
        assertEquals(HttpStatusCode.Unauthorized, client.unpair("falsch", child.deviceId).status)
        assertEquals(HttpStatusCode.Forbidden, client.unpair(child.deviceToken, parent.deviceId).status)
        assertEquals(HttpStatusCode.BadRequest, client.unpair(parent.deviceToken, "gibt-es-nicht").status)
        client.unpair(parent.deviceToken, child.deviceId)
        assertEquals(HttpStatusCode.BadRequest, client.unpair(parent.deviceToken, child.deviceId).status, "schon entkoppelt")
    }

    @Test
    fun `Ein neues Kindgerät ersetzt das alte, das alte bekommt 401, der Code ist verbraucht`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val old = server.pairChild(client, parent, "Altes Tablet")
        val code = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = old.childId))
        assertEquals("Altes Tablet", code.replacesDeviceName)

        val response = client.redeem(code.code, "Neues Tablet")

        assertEquals(HttpStatusCode.OK, response.status)
        val new = response.body<RedeemPairingCodeResponse>()
        assertEquals(old.childId, new.childId)
        assertEquals(HttpStatusCode.Unauthorized, client.sync(old.deviceToken).status)
        assertEquals(HttpStatusCode.OK, client.sync(new.deviceToken).status)
        val devices = client.parentSnapshot(parent).devices
        assertEquals(setOf(DeviceRole.PARENT, DeviceRole.CHILD), devices.map { it.role }.toSet())
        assertEquals(2, devices.size)
        assertEquals("Neues Tablet", devices.single { it.role == DeviceRole.CHILD }.name)
        assertEquals(1, server.services.children.count(), "Das Kind bleibt")
        assertEquals(HttpStatusCode.BadRequest, client.redeem(code.code, "Noch eins").status, "Einmalig")
    }

    @Test
    fun `Pro Kind gibt es höchstens ein aktives Kindgerät, auch nach mehrfachem Ersetzen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val first = server.pairChild(client, parent, "Erstes")
        repeat(2) { n ->
            val code = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = first.childId))
            assertEquals(HttpStatusCode.OK, client.redeem(code.code, "Tablet ${n + 2}").status)
        }

        val active = client.parentSnapshot(parent).devices.filter { it.role == DeviceRole.CHILD }
        assertEquals(listOf("Tablet 3"), active.map { it.name })
    }

    @Test
    fun `Die laufende Sitzung steht nach dem Ersetzen im Snapshot des neuen Kindgeräts`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val old = server.pairChild(client, parent)
        val sessionId = UUID.randomUUID().toString()
        client.sync(old.deviceToken, SessionUpdate(sessionId, startAgeMillis = 0))
        server.clock.advance(Duration.ofMinutes(10))

        val code = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = old.childId))
        val new = client.redeem(code.code, "Neues Tablet").body<RedeemPairingCodeResponse>()
        val snapshot = client.sync(new.deviceToken).body<ChildSnapshot>()

        assertEquals(sessionId, snapshot.status.runningSession?.id)
        assertEquals(Duration.ofMinutes(10).toMillis(), snapshot.status.usedMillis)
    }

    @Test
    fun `Entkoppelte Geräte bekommen keine Push-Signale mehr, die Elterngeräte erfahren von der neuen Liste`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val mama = server.pairDevice(client, DeviceRole.PARENT, "Handy Mama")
        val papa = server.pairDevice(client, DeviceRole.PARENT, "Handy Papa")
        val mamaKeys = WebPushReceiver()
        val papaKeys = WebPushReceiver()
        for ((device, keys, topic) in listOf(Triple(mama, mamaKeys, "upMAMAtopic0001"), Triple(papa, papaKeys, "upPAPAtopic0001"))) {
            val registration = PushRegistration("https://ntfy.example.org/$topic?up=1", keys.publicKeyBase64Url, keys.authSecretBase64Url)
            client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
                header("Authorization", "Bearer ${device.deviceToken}")
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
        }

        client.unpair(mama.deviceToken, papa.deviceId)
        server.services.push.flush()
        assertEquals(listOf("/upMAMAtopic0001?up=1"), ntfy.requests.map { it.pathAndQuery }, "Nur Mama erfährt von der neuen Liste")

        ntfy.requests.clear()
        server.services.push.signalParents()
        server.services.push.flush()
        assertEquals(listOf("/upMAMAtopic0001?up=1"), ntfy.requests.map { it.pathAndQuery })
    }

    @Test
    fun `Ein ersetztes Kindgerät bekommt keine Push-Signale mehr, das neue schon`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val old = server.pairChild(client, parent)
        val oldKeys = WebPushReceiver()
        client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            header("Authorization", "Bearer ${old.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(PushRegistration("https://ntfy.example.org/upALTtopic00001?up=1", oldKeys.publicKeyBase64Url, oldKeys.authSecretBase64Url))
        }
        val code = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = old.childId))
        val new = client.redeem(code.code, "Neues Tablet").body<RedeemPairingCodeResponse>()
        val newKeys = WebPushReceiver()
        client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            header("Authorization", "Bearer ${new.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(PushRegistration("https://ntfy.example.org/upNEUtopic00001?up=1", newKeys.publicKeyBase64Url, newKeys.authSecretBase64Url))
        }

        server.services.push.signalChild(checkNotNull(old.childId))
        server.services.push.flush()

        assertEquals(listOf("/upNEUtopic00001?up=1"), ntfy.requests.map { it.pathAndQuery })
        assertNotNull(server.services.devices.listActive().singleOrNull { it.role == DeviceRole.CHILD })
        assertTrue(server.services.devices.listActive().none { it.id == old.deviceId })
    }

    @Test
    fun `Ein Automatischer Stopp schickt Resync-Signale an das Kindgerät und die Elterngeräte`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChild(client, parent)
        for ((device, topic) in listOf(parent to "upELTERNtopic01", child to "upKINDtopic0001")) {
            val keys = WebPushReceiver()
            client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
                header("Authorization", "Bearer ${device.deviceToken}")
                contentType(ContentType.Application.Json)
                setBody(PushRegistration("https://ntfy.example.org/$topic?up=1", keys.publicKeyBase64Url, keys.authSecretBase64Url))
            }
        }
        client.sync(child.deviceToken, SessionUpdate(UUID.randomUUID().toString(), startAgeMillis = 0))
        server.services.push.flush()
        ntfy.requests.clear()

        server.clock.advance(Duration.ofMinutes(130))
        server.services.scheduler.runDue()
        server.services.push.flush()

        assertEquals(setOf("/upELTERNtopic01?up=1", "/upKINDtopic0001?up=1"), ntfy.requests.map { it.pathAndQuery }.toSet())
    }
}
