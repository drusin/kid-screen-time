package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PairingQr
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.qr.QrScanResult
import io.github.drusin.kidscreentime.ui.pairing.PairDeviceViewModel
import io.github.drusin.kidscreentime.ui.pairing.PairError
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PairDeviceViewModelTest {
    private val app = TestContainer()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PairDeviceViewModel(app.container.pairingRepository, defaultDeviceName = "Pixel")

    @Test
    fun `Koppeln ist erst mit Server-URL, Code und Gerätename möglich`() {
        val viewModel = viewModel()
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onServerUrlChange("kst.example.org")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onCodeChange("ABCD-EFGH")

        assertTrue(viewModel.state.value.canSubmit)
        assertEquals("Pixel", viewModel.state.value.deviceName)
    }

    @Test
    fun `Erfolgreiche Kopplung macht die App zum gekoppelten Gerät`() = runTest {
        app.handler = { respondJson("""{"deviceId":"d1","deviceToken":"tok","role":"PARENT"}""") }
        val viewModel = viewModel()
        viewModel.onServerUrlChange("kst.example.org")
        viewModel.onCodeChange("ABCD-EFGH")

        viewModel.submit()
        viewModel.state.await { !it.submitting }

        assertIs<DeviceState.Paired>(app.container.pairingRepository.state.value)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `Ungültiger Code zeigt einen Fehler, der bei der nächsten Eingabe verschwindet`() = runTest {
        app.handler = { respond("", HttpStatusCode.BadRequest) }
        val viewModel = viewModel()
        viewModel.onServerUrlChange("kst.example.org")
        viewModel.onCodeChange("XXXX-XXXX")

        viewModel.submit()
        viewModel.state.await { !it.submitting }

        assertEquals(PairError.INVALID_CODE, viewModel.state.value.error)
        assertEquals(DeviceState.Unpaired, app.container.pairingRepository.state.value)
        viewModel.onCodeChange("ABCD-EFGH")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `Nicht erreichbarer Server zeigt einen Fehler`() = runTest {
        app.handler = { throw IOException("offline") }
        val viewModel = viewModel()
        viewModel.onServerUrlChange("kst.example.org")
        viewModel.onCodeChange("ABCD-EFGH")

        viewModel.submit()
        viewModel.state.await { !it.submitting }

        assertEquals(PairError.SERVER_UNREACHABLE, viewModel.state.value.error)
    }

    @Test
    fun `Ein gescannter Kopplungs-QR-Code füllt Server-URL und Code und koppelt sofort`() = runTest {
        app.handler = { respondJson("""{"deviceId":"d1","deviceToken":"tok","role":"CHILD","childId":"k1","childName":"Mia"}""") }
        val viewModel = viewModel()

        viewModel.onQrResult(QrScanResult.Scanned(PairingQr.encode("https://kst.example.org", "ABCD-EFGH")))
        viewModel.state.await { !it.submitting }

        assertEquals("https://kst.example.org", viewModel.state.value.serverUrl)
        assertEquals("ABCD-EFGH", viewModel.state.value.code)
        val paired = assertIs<DeviceState.Paired>(app.container.pairingRepository.state.value)
        assertEquals(DeviceRole.CHILD, paired.device.role)
        assertEquals("Mia", paired.device.childName)
    }

    @Test
    fun `Ein fremder QR-Code zeigt einen Fehler und koppelt nicht`() = runTest {
        val viewModel = viewModel()

        viewModel.onQrResult(QrScanResult.Scanned("https://example.org/irgendwas"))

        assertEquals(PairError.INVALID_QR, viewModel.state.value.error)
        assertEquals(DeviceState.Unpaired, app.container.pairingRepository.state.value)
        assertTrue(app.requests.isEmpty())
    }

    @Test
    fun `Abbrechen des Scanners ändert nichts, ein nicht verfügbarer Scanner verweist auf das Eintippen`() = runTest {
        val viewModel = viewModel()

        viewModel.onQrResult(QrScanResult.Cancelled)
        assertNull(viewModel.state.value.error)

        viewModel.onQrResult(QrScanResult.Unavailable)
        assertEquals(PairError.SCANNER_UNAVAILABLE, viewModel.state.value.error)
        viewModel.onServerUrlChange("kst.example.org")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `Hat das Kind schon ein Kindgerät, meldet die Kopplung den Konflikt`() = runTest {
        app.handler = { respond("", HttpStatusCode.Conflict) }
        val viewModel = viewModel()
        viewModel.onServerUrlChange("kst.example.org")
        viewModel.onCodeChange("ABCD-EFGH")

        viewModel.submit()
        viewModel.state.await { !it.submitting }

        assertEquals(PairError.CHILD_HAS_DEVICE, viewModel.state.value.error)
        assertEquals(DeviceState.Unpaired, app.container.pairingRepository.state.value)
    }
}
