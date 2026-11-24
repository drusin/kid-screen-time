package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.pairing.DeviceState
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
}
