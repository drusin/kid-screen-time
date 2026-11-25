package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.MainDispatcherRule
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.ui.parent.ParentHomeViewModel
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ticket 17: Das Elterngerät offline zeigt den letzten bekannten Stand, alle Aktionen sind aus. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ParentOfflineTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = TestContainer()

    private fun snapshot(adjustment: Int = 25, childSeen: Long = 1_000) =
        """{"serverTimeEpochMillis":2000,"timeZone":"Europe/Berlin","devices":[""" +
            """{"id":"d1","name":"Handy","role":"PARENT","pairedAtEpochMillis":1},""" +
            """{"id":"d2","name":"Tablet","role":"PARENT","pairedAtEpochMillis":1},""" +
            """{"id":"t1","name":"Tablet Mia","role":"CHILD","childId":"k1","pairedAtEpochMillis":1,"lastSeenEpochMillis":$childSeen}],""" +
            """"children":[{"childId":"k1","name":"Mia","status":{"day":"2026-01-05",""" +
            """"allowance":{"templateMinutes":60,"adjustmentMinutes":$adjustment,"totalMinutes":${60 + adjustment}},"usedMillis":0,""" +
            """"remainingMillis":${(60 + adjustment) * 60_000L},"overrunMillis":0,"state":"READY"},"lastAdjustmentMinutes":$adjustment}]}"""

    @Before
    fun setUp() {
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}""")
                else -> respondJson(snapshot())
            }
        }
        runBlocking { app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy") }
    }

    private suspend fun onlineViewModel(): ParentHomeViewModel =
        main.track(ParentHomeViewModel(app.container.parentRepository)).also { it.state.await { s -> !s.loading } }

    private fun goOffline() {
        app.handler = { throw IOException("offline") }
    }

    @Test
    fun `Ein neuer Start ohne Netz zeigt den zuletzt geladenen Stand aus dem Cache, Offline und nur zur Ansicht`() = runTest {
        onlineViewModel()
        goOffline()

        val offline = main.track(ParentHomeViewModel(app.container.parentRepository))
        val state = offline.state.await { !it.loading }

        assertTrue(state.offline)
        assertEquals(85, state.child?.status?.allowance?.totalMinutes, "der Stand von vorhin")
        assertEquals(2, state.devices.count { it.role == DeviceRole.PARENT })
        assertFalse(state.showNoChildHint)
    }

    @Test
    fun `Ohne Netz und ohne Cache bleibt der Bildschirm leer, ist aber offline`() = runTest {
        goOffline()

        val state = main.track(ParentHomeViewModel(app.container.parentRepository)).state.await { !it.loading }

        assertTrue(state.offline)
        assertTrue(state.children.isEmpty())
    }

    @Test
    fun `Offline sind Buchen, Zurücknehmen, Wochenvorlage, Regeln, Entkoppeln und Koppeln deaktiviert und nichts geht an den Server`() = runTest {
        onlineViewModel()
        goOffline()
        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading && it.offline }
        viewModel.stepAdjustment(10)
        app.requests.clear()
        val state = viewModel.state.value

        assertFalse(state.canAct, "der eine Schalter für alle Aktionen, auch für Koppeln")
        assertFalse(state.canBook)
        assertTrue(state.devices.none { state.canUnpair(it) })
        viewModel.bookAdjustment()
        viewModel.undoLastAdjustment()
        viewModel.saveWeeklyTemplate(List(7) { 30 })
        viewModel.saveRules("Mia", 5, 60)
        viewModel.unpairDevice("d2")
        viewModel.unpairDevice("t1")

        assertTrue(app.requests.isEmpty(), "offline geht keine Aktion los: ${app.requests.map { it.url }}")
        assertFalse(viewModel.state.value.acting)
    }

    @Test
    fun `Scheitert eine Aktion am Netz, wird das Gerät offline, bis ein Abgleich wieder gelingt`() = runTest {
        val viewModel = onlineViewModel()
        assertFalse(viewModel.state.value.offline)
        goOffline()
        viewModel.stepAdjustment(10)
        viewModel.bookAdjustment()
        viewModel.state.await { it.actionFailed }

        assertTrue(viewModel.state.value.offline)
        assertFalse(viewModel.state.value.canBook)
        assertEquals(10, viewModel.state.value.pendingAdjustment, "die Eingabe bleibt")

        app.handler = { respondJson(snapshot(adjustment = 30)) }
        viewModel.refreshIfOffline()
        val back = viewModel.state.await { !it.loading && !it.offline }

        assertTrue(back.canBook)
        assertEquals(90, back.child?.status?.allowance?.totalMinutes)
    }

    @Test
    fun `Jede Aktion liefert den neuen Stand, der auch im Cache landet`() = runTest {
        val viewModel = onlineViewModel()
        app.handler = { request ->
            if (request.url.encodedPath == ApiPaths.PARENT_DAILY_ADJUSTMENTS) respondJson(snapshot(adjustment = 35)) else respondJson(snapshot())
        }
        viewModel.stepAdjustment(10)
        viewModel.bookAdjustment()
        viewModel.state.await { it.child?.status?.allowance?.totalMinutes == 95 }
        goOffline()

        val offline = main.track(ParentHomeViewModel(app.container.parentRepository)).state.await { !it.loading }

        assertEquals(95, offline.child?.status?.allowance?.totalMinutes, "der Stand nach der Aktion")
    }

    @Test
    fun `Die Statuskarte kennt den Stand des Kindgeräts seit dessen letztem Kontakt`() = runTest {
        app.handler = { respondJson(snapshot(childSeen = 1_234)) }

        assertEquals(1_234L, onlineViewModel().state.value.knownSinceEpochMillis)
    }

    @Test
    fun `Ohne Kindgerät in der Geräteliste gibt es kein Stand bekannt seit`() = runTest {
        app.handler = { respondJson("""{"serverTimeEpochMillis":1,"devices":[],"children":[{"childId":"k1","name":"Mia"}]}""") }

        assertNull(onlineViewModel().state.value.knownSinceEpochMillis)
    }

    @Test
    fun `Eine Antwort 401 ist nicht offline, sondern entkoppelt das Gerät und leert den Cache`() = runTest {
        onlineViewModel()
        app.handler = { respond("", HttpStatusCode.Unauthorized) }

        val state = main.track(ParentHomeViewModel(app.container.parentRepository)).state.await { !it.loading }

        assertFalse(state.offline)
        assertEquals(DeviceState.Revoked, app.container.pairingRepository.state.value)
        assertNull(app.database.parentSnapshotDao().current(), "nach dem Entkoppeln bleibt kein Stand der Familie auf dem Gerät")
    }
}
