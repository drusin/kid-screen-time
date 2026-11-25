package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.api.SetupItem
import io.github.drusin.kidscreentime.domain.clock.ManualClock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import org.junit.jupiter.api.io.TempDir

/**
 * Ticket 15: Einrichtungs-Checkliste. The Kindgerät reports its missing prüfbare Punkte with every sync; the server keeps
 * the last list per Kindgerät, shows it in the Eltern-Snapshot and notifies all Elterngeräte exactly once per Übergang
 * from "vollständig" to "etwas fehlt". Pushes arrive at a [FakeNtfy] and are decrypted like a Elterngerät does.
 */
class SetupChecklistApiTest {
    @TempDir
    lateinit var dir: Path

    private val ntfy = FakeNtfy()

    @AfterTest
    fun stopNtfy() = ntfy.close()

    private class Parent(val receiver: WebPushReceiver, val topic: String, val token: String)

    private suspend fun TestServer.pairParent(client: HttpClient, topic: String): Parent {
        val paired = pairDevice(client, DeviceRole.PARENT, name = topic)
        val parent = Parent(WebPushReceiver(), topic, paired.deviceToken)
        client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            header("Authorization", "Bearer ${paired.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(PushRegistration("https://ntfy.example.org/$topic?up=1", parent.receiver.publicKeyBase64Url, parent.receiver.authSecretBase64Url))
        }
        return parent
    }

    /** A sync with the given Checkliste ([missing] null: not reported). */
    private suspend fun HttpClient.sync(device: RedeemPairingCodeResponse, missing: List<SetupItem>?, vararg updates: SessionUpdate) =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(updates.toList(), missing))
        }

    private suspend fun HttpClient.shownToParent(parent: Parent): List<SetupItem> =
        get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.token}") }
            .body<ParentSnapshot>().children.single().missingSetupItems

    /** The texts one Elterngerät received so far (signals without text are plain Resync-Signale). */
    private fun TestServer.texts(parent: Parent): List<String> {
        services.push.flush()
        return ntfy.requests.filter { it.pathAndQuery == "/${parent.topic}?up=1" }
            .mapNotNull { ApiJson.decodeFromString(PushSignal.serializer(), parent.receiver.decrypt(it.body).decodeToString()).text }
    }

    private fun TestServer(clockOf: TestServer? = null) = TestServer(dir, clockOf?.clock ?: ManualClock(), ntfyInternalUrl = ntfy.url)

    @Test
    fun `Fehlt ein Punkt, bekommen alle Elterngeräte genau eine Benachrichtigung mit dem Kindnamen vorne`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val first = server.pairParent(client, "upELTERN0001")
        val second = server.pairParent(client, "upELTERN0002")
        val child = server.pairDevice(client, DeviceRole.CHILD)

        client.sync(child, emptyList())
        assertEquals(emptyList(), server.texts(first), "Vollständig: nichts zu melden")

        client.sync(child, listOf(SetupItem.ALARM_VOLUME))

        assertEquals(listOf("Mia: Alarmlautstärke ist 0"), server.texts(first))
        assertEquals(listOf("Mia: Alarmlautstärke ist 0"), server.texts(second))
    }

    @Test
    fun `Solange etwas fehlt, kommt keine zweite Benachrichtigung, auch nicht bei weiteren fehlenden Punkten oder wiederholtem Sync`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)

        client.sync(child, listOf(SetupItem.ALARM_VOLUME))
        client.sync(child, listOf(SetupItem.ALARM_VOLUME))
        client.sync(child, listOf(SetupItem.ALARM_VOLUME, SetupItem.BATTERY))
        client.sync(child, listOf(SetupItem.BATTERY))
        client.sync(child, null)

        assertEquals(listOf("Mia: Alarmlautstärke ist 0"), server.texts(parent))
    }

    @Test
    fun `Erst nach einer vollständigen Meldung ist die nächste Benachrichtigung möglich`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)

        client.sync(child, listOf(SetupItem.ALARM_VOLUME))
        client.sync(child, emptyList())
        client.sync(child, listOf(SetupItem.NOTIFICATIONS, SetupItem.EXACT_ALARMS))

        assertEquals(
            listOf("Mia: Alarmlautstärke ist 0", "Mia: Exakte Alarme sind nicht erlaubt; Benachrichtigungen sind aus"),
            server.texts(parent),
        )
    }

    @Test
    fun `Nach einem Neustart des Servers kommt dieselbe fehlende Liste nicht noch einmal`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        client.sync(child, listOf(SetupItem.PUSH_DISTRIBUTOR))

        // Same database file, new process: the Vermerk lives in the database, so the same Liste is no new Übergang.
        val restarted = TestServer(server)
        val childId = restarted.services.children.childIds().single()
        val again = restarted.services.children.sync(childId, emptyList(), child.deviceId, listOf(SetupItem.PUSH_DISTRIBUTOR))

        assertEquals(null, (again as SyncResult.Synced).setupTransition)
        assertEquals(listOf("Mia: Kein Push-Verteiler verbunden"), server.texts(parent))
    }

    @Test
    fun `Der Eltern-Snapshot zeigt die zuletzt gemeldeten fehlenden Punkte und ist nach einer vollständigen Meldung leer`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        assertEquals(emptyList(), client.shownToParent(parent), "Noch nichts gemeldet")

        client.sync(child, listOf(SetupItem.BATTERY, SetupItem.ALARM_VOLUME))
        assertEquals(listOf(SetupItem.BATTERY, SetupItem.ALARM_VOLUME), client.shownToParent(parent))

        client.sync(child, listOf(SetupItem.BATTERY))
        assertEquals(listOf(SetupItem.BATTERY), client.shownToParent(parent), "Nur die letzte Liste zählt")

        client.sync(child, null)
        assertEquals(listOf(SetupItem.BATTERY), client.shownToParent(parent), "Ohne Meldung bleibt die letzte Liste")

        client.sync(child, emptyList())
        assertEquals(emptyList(), client.shownToParent(parent))
    }

    @Test
    fun `Ein wiederholter Sync mit denselben Sitzungen und derselben Liste ändert nichts und meldet nichts neu`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val session = SessionUpdate(UUID.randomUUID().toString(), startAgeMillis = 0)

        repeat(3) {
            client.sync(child, listOf(SetupItem.ALARM_VOLUME), session)
            server.clock.advance(Duration.ofSeconds(30))
        }

        assertEquals(listOf("Mia: Alarmlautstärke ist 0"), server.texts(parent))
        assertEquals(listOf(SetupItem.ALARM_VOLUME), client.shownToParent(parent))
    }

    @Test
    fun `Das ersetzende Kindgerät beginnt vollständig, die Liste des alten ist weg`() = testApplication {
        val server = TestServer().also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairParent(client, "upELTERN0001")
        val old = server.pairDevice(client, DeviceRole.CHILD)
        client.sync(old, listOf(SetupItem.ALARM_VOLUME))
        val childId = server.services.children.childIds().single()
        val replacement = client.redeem(server.services.pairing.createCode(DeviceRole.CHILD, childId = childId).code, "Neues Tablet").body<RedeemPairingCodeResponse>()
        assertEquals(emptyList(), client.shownToParent(parent))

        client.sync(replacement, listOf(SetupItem.ALARM_VOLUME))

        assertEquals(listOf("Mia: Alarmlautstärke ist 0", "Mia: Alarmlautstärke ist 0"), server.texts(parent), "Neues Gerät, neuer Übergang")
    }
}
