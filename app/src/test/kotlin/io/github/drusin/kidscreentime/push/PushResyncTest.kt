package io.github.drusin.kidscreentime.push

import io.github.drusin.kidscreentime.waitFor
import io.github.drusin.kidscreentime.MainDispatcherRule
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.KidScreenTimeApp
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.pairing.PairResult
import io.github.drusin.kidscreentime.sync.SyncScenario
import io.github.drusin.kidscreentime.sync.syncScenario
import io.github.drusin.kidscreentime.ui.TestContainer
import io.github.drusin.kidscreentime.ui.parent.ParentHomeViewModel
import io.github.drusin.kidscreentime.ui.await
import io.github.drusin.kidscreentime.ui.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.unifiedpush.android.connector.data.PublicKeySet
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** Ticket 13: Push-Signale lösen am Gerät einen Sync aus, und die Push-Registrierung kommt zum Server. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PushResyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val signalBytes = """{"title":"Mia","text":"Zeit ist um"}""".toByteArray()

    // The WebPush keys of RFC 8291, section 5 (any valid P-256 key and 16-byte secret would do).
    private val publicKey = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    private val authSecret = "BTBZMqHH6r4Tts7J_aSIgg"
    private val ntfyTopicUrl = "https://ntfy.example.org/upAbCdEfGh1234?up=1"

    @get:Rule
    val main = MainDispatcherRule()

    /**
     * The service as the connector would run it. Robolectric creates it on the test thread, so it exists before the
     * scenario starts; [useContainerOf] then puts the container of a simulated device behind the Application.
     */
    private fun pushService(): ResyncPushService = Robolectric.setupService(ResyncPushService::class.java)

    private fun useContainerOf(device: SyncScenario.Device) {
        (ApplicationProvider.getApplicationContext<Context>() as KidScreenTimeApp).injectContainerForTest(device.app.container)
    }

    @Test
    fun `Ein empfangenes Push-Signal löst am Kindgerät einen Sync aus`() = pushService().let { service -> syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        useContainerOf(child)
        sync()
        val id = start() // lies only in the Outbox until a sync delivers it
        assertNull(runCatching { serverSession(id) }.getOrNull())
        requests.clear()

        service.onMessage(PushMessage(signalBytes, decrypted = true), "default")

        waitFor { child.outbox().isEmpty() }
        assertEquals(listOf("POST ${ApiPaths.CHILD_SYNC}"), requests)
        assertEquals(server.clock.now().toEpochMilli(), serverSession(id).first)
    } }

    @Test
    fun `Eine Nachricht, die nicht entschlüsselt werden konnte, löst keinen Sync aus`() = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        sync()
        requests.clear()
        val handler = child.app.container.pushHandler

        handler.onMessage(signalBytes, decrypted = false)

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `Ein ungelesener Body ist trotzdem ein Resync-Signal, weil das Signal keinen Stand trägt`() = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        sync()
        requests.clear()

        child.app.container.pushHandler.onMessage("kein json".toByteArray(), decrypted = true)

        assertEquals(listOf("POST ${ApiPaths.CHILD_SYNC}"), requests)
    }

    @Test
    fun `Am Elterngerät meldet das Push-Signal die Aktualisierung und der Snapshot wird neu geladen`() = runTest {
        val app = TestContainer()
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}""")
                else -> respondJson("""{"serverTimeEpochMillis":0,"timeZone":"Europe/Berlin","devices":[],"children":[]}""")
            }
        }
        runBlocking { app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy") }
        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository, app.container.pushHandler.signals))
        runBlocking { app.snapshotRequests(1) }
        app.requests.clear()

        app.container.pushHandler.onMessage(signalBytes, decrypted = true)

        runBlocking { app.snapshotRequests(1) }
        viewModel.state.await { !it.loading }
        assertEquals(1, app.requests.count { it.url.encodedPath == ApiPaths.PARENT_SNAPSHOT }, "Genau ein neuer Abgleich")
    }

    private suspend fun TestContainer.snapshotRequests(count: Int) =
        waitFor("$count Abgleich(e) des Eltern-Snapshots") { requests.count { it.url.encodedPath == ApiPaths.PARENT_SNAPSHOT } >= count }

    @Test
    fun `Das Signal erreicht Abonnenten mit seinem Text, damit Benachrichtigungen ihn zeigen können`() = runTest {
        val app = TestContainer()
        val received = mutableListOf<PushSignal>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            app.container.pushHandler.signals.collect { received += it }
        }

        app.container.pushHandler.onMessage(signalBytes, decrypted = true)

        assertEquals(listOf(PushSignal("Mia", "Zeit ist um")), received)
        job.cancel()
    }

    @Test
    fun `Der Endpoint des Verteilers wird nach dem Koppeln mit Schlüsseln beim Server registriert`() = pushService().let { service -> syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        useContainerOf(child)

        service.onNewEndpoint(PushEndpoint(ntfyTopicUrl, PublicKeySet(publicKey, authSecret), false), "default")

        waitFor { storedPush() != null }
        assertEquals(Triple(ntfyTopicUrl, publicKey, authSecret), storedPush())
    } }

    private fun SyncScenario.storedPush(): Triple<String, String, String>? = server.services.database.transaction { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT push_endpoint, push_public_key, push_auth_secret FROM devices WHERE role = 'CHILD'").use { rs ->
                if (rs.next() && rs.getString(1) != null) Triple(rs.getString(1), rs.getString(2), rs.getString(3)) else null
            }
        }
    }

    @Test
    fun `Ein Endpoint vor dem Koppeln wird gemerkt und nach dem Koppeln gesendet, ohne Wiederholung`() = runTest {
        val app = TestContainer()
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}""")
                else -> respond204()
            }
        }
        val push = app.container.pushRepository
        val registration = PushRegistration(ntfyTopicUrl, publicKey, authSecret)

        push.onNewEndpoint(registration) // not paired yet
        assertTrue(app.requests.isEmpty())

        assertEquals(PairResult.Success, app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy"))
        app.requests.clear()
        assertTrue(push.submit())
        assertTrue(push.submit(), "Schon bekannt: kein zweiter Aufruf")

        val request = app.requests.single()
        assertEquals("https://kst.example.org" + ApiPaths.PUSH_REGISTRATION, request.url.toString())
        assertEquals("Bearer tok-123", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `Scheitert die Registrierung am Netz, wird sie beim nächsten Versuch wiederholt und ein neuer Endpoint ersetzt den alten`() = runTest {
        val app = TestContainer()
        var online = false
        app.handler = { request ->
            when {
                request.url.encodedPath == ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}""")
                !online -> throw IOException("offline")
                else -> respond204()
            }
        }
        app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy")
        val push = app.container.pushRepository

        push.onNewEndpoint(PushRegistration(ntfyTopicUrl, publicKey, authSecret))
        assertEquals(false, push.submit())

        online = true
        assertTrue(push.submit())
        app.requests.clear()
        push.onNewEndpoint(PushRegistration("https://ntfy.example.org/upNEUERendpoint?up=1", publicKey, authSecret))
        assertEquals(1, app.requests.size)
    }

    private fun MockRequestHandleScope.respond204() = respond("", HttpStatusCode.NoContent)
}
