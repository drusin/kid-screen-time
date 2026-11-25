package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.MainDispatcherRule
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PairingQr
import io.github.drusin.kidscreentime.ui.parent.CreateCodeError
import io.github.drusin.kidscreentime.ui.parent.PairNewDeviceViewModel
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PairNewDeviceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = TestContainer()
    private var snapshot = SNAPSHOT_WITHOUT_CHILD
    private var createCodeStatus = HttpStatusCode.OK
    /** Bodies of the requests that asked the server for a Kopplungscode. */
    private val createCodeBodies: List<String>
        get() = app.requests.filter { it.url.encodedPath == ApiPaths.PARENT_PAIRING_CODES }
            .map { request -> runBlocking { String(request.body.toByteArray()) } }

    @Before
    fun setUp() {
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-1","role":"PARENT"}""")
                ApiPaths.PARENT_PAIRING_CODES -> {
                    if (createCodeStatus == HttpStatusCode.OK) respondJson(codeResponse(snapshot)) else respond("", createCodeStatus)
                }
                else -> respondJson(snapshot)
            }
        }
        runBlocking { app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy von Mama") }
        app.requests.clear()
    }

    private fun codeResponse(snapshotJson: String, replaces: String? = null, childName: String? = "Mia") = buildString {
        val now = app.clock.now().toEpochMilli()
        append("""{"code":"ABCD-EFGH","expiresAtEpochMillis":${now + 600_000},"serverTimeEpochMillis":$now,"serverUrl":"https://kst.example.org",""")
        append(""""role":"CHILD","childName":"$childName",""")
        if (replaces != null) append(""""replacesDeviceName":"$replaces",""")
        append(""""snapshot":$snapshotJson}""")
    }

    private fun viewModel() = main.track(PairNewDeviceViewModel(app.container.parentRepository, app.container.monotonicClock))

    @Test
    fun `Gibt es noch kein Kind, fragt das Koppeln eines Kindgeräts zuerst nach dem Namen`() = runTest {
        val viewModel = viewModel()
        viewModel.state.await { !it.loading }

        viewModel.onRoleSelected(DeviceRole.CHILD)

        assertTrue(viewModel.state.value.needsChildName)
        assertFalse(viewModel.state.value.canCreateChildCode)
        assertTrue(createCodeBodies.isEmpty())

        viewModel.onNewChildNameChange("  Mia ")
        assertTrue(viewModel.state.value.canCreateChildCode)
    }

    @Test
    fun `Der Name des neuen Kindes geht mit der Anfrage an den Server und der Code erscheint als QR-Code mit Server-URL und als Text`() = runTest {
        val viewModel = viewModel()
        viewModel.state.await { !it.loading }
        viewModel.onRoleSelected(DeviceRole.CHILD)
        viewModel.onNewChildNameChange("Mia")

        viewModel.createCode()
        val code = viewModel.state.await { it.code != null }.code!!

        assertTrue(createCodeBodies.single().contains("\"newChildName\":\"Mia\"") && createCodeBodies.single().contains("\"role\":\"CHILD\""))
        assertEquals("ABCD-EFGH", code.code)
        assertEquals(PairingQr.Content("https://kst.example.org", "ABCD-EFGH"), PairingQr.parse(code.qrContent))
        assertEquals(Duration.ofMinutes(10), code.remaining)
        assertEquals("Mia", code.childName)
    }

    @Test
    fun `Der Countdown läuft mit der monotonen Uhr und der Code gilt danach als abgelaufen`() = runTest {
        val viewModel = viewModel()
        viewModel.state.await { !it.loading }
        viewModel.onRoleSelected(DeviceRole.CHILD)
        viewModel.onNewChildNameChange("Mia")
        viewModel.createCode()
        viewModel.state.await { it.code != null }

        app.clock.advance(Duration.ofMinutes(9).plusSeconds(30))
        viewModel.tick()
        assertEquals(Duration.ofSeconds(30), viewModel.state.value.code!!.remaining)
        assertFalse(viewModel.state.value.code!!.expired)

        app.clock.advance(Duration.ofMinutes(1))
        viewModel.tick()
        assertTrue(viewModel.state.value.code!!.expired)
        assertEquals(Duration.ZERO, viewModel.state.value.code!!.remaining)
    }

    @Test
    fun `Hat das Kind schon ein Kindgerät, zeigt das Koppeln den Ersetzen-Hinweis und schickt die Kind-ID`() = runTest {
        snapshot = SNAPSHOT_WITH_CHILD_DEVICE
        val viewModel = viewModel()
        val loaded = viewModel.state.await { !it.loading }

        assertEquals("Mia", loaded.child?.name)
        assertEquals("Tablet von Mia", loaded.replacesDeviceName)

        viewModel.onRoleSelected(DeviceRole.CHILD)
        assertFalse(viewModel.state.value.needsChildName)
        assertTrue(viewModel.state.value.canCreateChildCode)
        viewModel.createCode()
        viewModel.state.await { it.code != null }

        assertTrue(createCodeBodies.single().contains("\"childId\":\"kind-1\""))
        assertFalse(createCodeBodies.single().contains("newChildName"))
    }

    @Test
    fun `Ohne Kindgerät gibt es keinen Ersetzen-Hinweis`() = runTest {
        snapshot = SNAPSHOT_WITH_CHILD_WITHOUT_DEVICE

        val state = viewModel().state.await { !it.loading }

        assertNotNull(state.child)
        assertNull(state.replacesDeviceName)
    }

    @Test
    fun `Ein Code für ein Elterngerät wird sofort erzeugt`() = runTest {
        val viewModel = viewModel()
        viewModel.state.await { !it.loading }

        viewModel.onRoleSelected(DeviceRole.PARENT)
        viewModel.state.await { it.code != null }

        assertTrue(createCodeBodies.single().contains("\"role\":\"PARENT\""))
    }

    @Test
    fun `Neuer Code nach Ablauf holt einen frischen Code vom Server`() = runTest {
        val viewModel = viewModel()
        viewModel.state.await { !it.loading }
        viewModel.onRoleSelected(DeviceRole.PARENT)
        viewModel.state.await { it.code != null }
        app.clock.advance(Duration.ofMinutes(11))
        viewModel.tick()
        assertTrue(viewModel.state.value.code!!.expired)

        viewModel.newCode()
        viewModel.state.await { it.code?.expired == false }

        assertEquals(2, createCodeBodies.size)
        assertEquals(Duration.ofMinutes(10), viewModel.state.value.code!!.remaining)
    }

    @Test
    fun `Lehnt der Server ab oder ist nicht erreichbar, zeigt das Koppeln einen Fehler`() = runTest {
        val viewModel = viewModel()
        viewModel.state.await { !it.loading }
        createCodeStatus = HttpStatusCode.BadRequest
        viewModel.onRoleSelected(DeviceRole.PARENT)
        assertEquals(CreateCodeError.REJECTED, viewModel.state.await { it.error != null }.error)

        app.handler = { throw IOException("offline") }
        viewModel.createCode()
        assertEquals(CreateCodeError.UNAVAILABLE, viewModel.state.await { it.error == CreateCodeError.UNAVAILABLE }.error)
        assertNull(viewModel.state.value.code)
    }

    @Test
    fun `Ist der Server beim Laden nicht erreichbar, bleibt der Bildschirm ohne Rollenwahl`() = runTest {
        app.handler = { throw IOException("offline") }

        val state = viewModel().state.await { !it.loading }

        assertTrue(state.unavailable)
    }

    private companion object {
        const val SNAPSHOT_WITHOUT_CHILD =
            """{"serverTimeEpochMillis":1,"devices":[{"id":"d1","name":"Handy von Mama","role":"PARENT","pairedAtEpochMillis":1}],"children":[]}"""
        const val CHILD = """{"childId":"kind-1","name":"Mia"}"""
        const val SNAPSHOT_WITH_CHILD_WITHOUT_DEVICE =
            """{"serverTimeEpochMillis":1,"devices":[{"id":"d1","name":"Handy von Mama","role":"PARENT","pairedAtEpochMillis":1}],"children":[$CHILD]}"""
        const val SNAPSHOT_WITH_CHILD_DEVICE =
            """{"serverTimeEpochMillis":1,"devices":[{"id":"d1","name":"Handy von Mama","role":"PARENT","pairedAtEpochMillis":1},""" +
                """{"id":"d2","name":"Tablet von Mia","role":"CHILD","childId":"kind-1","pairedAtEpochMillis":2}],"children":[$CHILD]}"""
    }
}
