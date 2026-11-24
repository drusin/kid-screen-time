package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DayAdjustment
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.SessionRecord
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import io.github.drusin.kidscreentime.ui.child.ChildHomeViewModel
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class ChildHomeViewModelTest {
    private val app = TestContainer()
    private val minute = 60_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        serve(testChildSnapshot(app.clock.now()))
        runBlocking { app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Tablet") }
        app.requests.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        app.database.close()
    }

    /** The server answers the pairing as a Kindgerät and then always with [snapshot]. */
    private fun serve(snapshot: io.github.drusin.kidscreentime.api.ChildSnapshot) {
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-kind","role":"CHILD","childId":"kind-1","childName":"Mia"}""")
                else -> respondJson(childSnapshotJson(snapshot))
            }
        }
    }

    private fun viewModel() = ChildHomeViewModel(app.container.childRepository, app.container.wallClock)

    @Test
    fun `Das Tablet ist nach der Kopplung ein Kindgerät`() {
        val device = (app.container.pairingRepository.state.value as io.github.drusin.kidscreentime.pairing.DeviceState.Paired).device

        assertEquals(DeviceRole.CHILD, device.role)
        assertEquals("Mia", device.childName)
    }

    @Test
    fun `Der Kind-Snapshot wird mit dem Geräte-Token geholt und zeigt Restzeit, Zustand und Tageskontingent mit Aufschlüsselung`() = runTest {
        serve(testChildSnapshot(app.clock.now(), adjustments = listOf(DayAdjustment("2026-01-05", 15, app.clock.now().toEpochMilli() - minute))))

        val viewModel = viewModel()
        val state = viewModel.state.await { it.stand != null }

        val request = app.requests.single()
        assertEquals("https://kst.example.org" + ApiPaths.CHILD_SNAPSHOT, request.url.toString())
        assertEquals("Bearer tok-kind", request.headers[HttpHeaders.Authorization])
        assertEquals("Mia", state.childName)
        val stand = state.stand!!
        assertEquals(Zustand.BEREIT, stand.zustand)
        assertEquals(Duration.ofMinutes(75), stand.restzeit)
        assertEquals(Duration.ofMinutes(60), stand.tageskontingent.vorlage)
        assertEquals(Duration.ofMinutes(15), stand.tageskontingent.anpassungen)
        assertEquals(Duration.ofMinutes(75), stand.tageskontingent.gesamt)
        assertEquals(Duration.ZERO, stand.verbrauch)
        assertFalse(state.offline)
    }

    @Test
    fun `Das Kindgerät rechnet lokal mit domain, auch wenn die Zeit vergeht`() = runTest {
        val now = app.clock.now()
        serve(testChildSnapshot(now, sessions = listOf(SessionRecord("s1", now.toEpochMilli() - 20 * minute))))
        val viewModel = viewModel()
        viewModel.state.await { it.stand != null }

        assertEquals(Duration.ofMinutes(40), viewModel.state.value.stand!!.restzeit)
        assertEquals(Zustand.LAEUFT, viewModel.state.value.stand!!.zustand)

        app.clock.advance(Duration.ofMinutes(37))
        viewModel.tick()

        val stand = viewModel.state.value.stand!!
        assertEquals(Duration.ofMinutes(3), stand.restzeit)
        assertEquals(Duration.ofMinutes(57), stand.verbrauch)
        assertEquals(Zustand.VORWARNUNG, stand.zustand)
        assertEquals("s1", stand.laufendeSitzung?.id)
    }

    @Test
    fun `Nach Mitternacht gilt die Wochenvorlage von morgen, ohne dass der Server gefragt wird`() = runTest {
        // Monday 09:00 Berlin: Monday 60 min, Tuesday 90 min (a version valid from tomorrow).
        serve(testChildSnapshot(app.clock.now(), templateToday = List(7) { 60 }, templateTomorrow = List(7) { 90 }))
        val viewModel = viewModel()
        viewModel.state.await { it.stand != null }
        assertEquals(Duration.ofMinutes(60), viewModel.state.value.stand!!.tageskontingent.gesamt)
        app.requests.clear()

        app.clock.advance(Duration.ofHours(15)) // Tuesday 00:00 Berlin
        viewModel.tick()

        assertEquals(Duration.ofMinutes(90), viewModel.state.value.stand!!.tageskontingent.gesamt)
        assertTrue(app.requests.isEmpty())
    }

    @Test
    fun `Das Kindgerät speichert den Snapshot in Room und rechnet ohne Netz mit dem gespeicherten Stand weiter`() = runTest {
        viewModel().state.await { it.stand != null }
        assertNotNull(app.container.childRepository.stored.first())

        app.handler = { throw IOException("offline") }
        app.clock.advance(Duration.ofMinutes(10))
        val offline = viewModel()
        val state = offline.state.await { it.stand != null && it.offline }

        assertEquals(Duration.ofMinutes(60), state.stand!!.restzeit)
        assertEquals("09:00", state.snapshotAt?.toLocalTime()?.toString())
    }

    @Test
    fun `Ohne Netz und ohne gespeicherten Stand gibt es keine Anzeige`() = runTest {
        app.handler = { throw IOException("offline") }

        val state = viewModel().state.await { !it.loading }

        assertNull(state.stand)
        assertTrue(state.offline)
    }

    @Test
    fun `Eine Antwort 401 lässt den gespeicherten Stand unberührt`() = runTest {
        app.handler = { respond("", HttpStatusCode.Unauthorized) }

        val state = viewModel().state.await { !it.loading }

        assertNull(state.stand)
        assertTrue(state.offline)
    }
}
