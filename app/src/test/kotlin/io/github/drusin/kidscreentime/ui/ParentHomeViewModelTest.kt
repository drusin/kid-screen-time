package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.ui.parent.ParentHomeViewModel
import io.github.drusin.kidscreentime.ui.parent.usedFraction
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ParentHomeViewModelTest {
    private val app = TestContainer()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}""")
                else -> respondJson(SNAPSHOT_WITHOUT_CHILDREN)
            }
        }
        runBlocking { app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy") }
        app.requests.clear()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `Ohne gekoppeltes Kind zeigt die Startseite den Hinweis`() = runTest {
        val viewModel = ParentHomeViewModel(app.container.parentRepository)
        viewModel.state.await { !it.loading }

        assertTrue(viewModel.state.value.showNoChildHint)
        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.unavailable)
    }

    @Test
    fun `Der Eltern-Snapshot wird mit dem Geräte-Token als Bearer-Header geholt`() = runTest {
        ParentHomeViewModel(app.container.parentRepository).state.await { !it.loading }

        val request = app.requests.single()
        assertEquals("https://kst.example.org" + ApiPaths.PARENT_SNAPSHOT, request.url.toString())
        assertEquals("Bearer tok-123", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `Ist der Server nicht erreichbar, bleibt der Hinweis und die Startseite meldet es`() = runTest {
        app.handler = { throw IOException("offline") }

        val viewModel = ParentHomeViewModel(app.container.parentRepository)
        viewModel.state.await { !it.loading }

        assertTrue(viewModel.state.value.unavailable)
        assertTrue(viewModel.state.value.showNoChildHint)
    }

    @Test
    fun `Eine Antwort 401 gilt als nicht verfügbar, bis Ticket 12 das Entkoppeln anzeigt`() = runTest {
        app.handler = { respond("", HttpStatusCode.Unauthorized) }

        val viewModel = ParentHomeViewModel(app.container.parentRepository)
        viewModel.state.await { !it.loading }

        assertTrue(viewModel.state.value.unavailable)
    }

    @Test
    fun `Die Statuskarte bekommt Restzeit, Verbrauch und Aufschlüsselung des Kindes vom Server`() = runTest {
        app.handler = { respondJson(SNAPSHOT_WITH_STATUS) }

        val viewModel = ParentHomeViewModel(app.container.parentRepository)
        viewModel.state.await { !it.loading }

        val child = viewModel.state.value.children.single()
        val status = child.status!!
        assertFalse(viewModel.state.value.showNoChildHint)
        assertEquals(45 * 60_000L, status.remainingMillis)
        assertEquals(40 * 60_000L, status.usedMillis)
        assertEquals(60, status.allowance.templateMinutes)
        assertEquals(25, status.allowance.adjustmentMinutes)
        assertEquals(85, status.allowance.totalMinutes)
        assertEquals(40f / 85f, status.usedFraction(), 0.001f)
    }

    private companion object {
        const val SNAPSHOT_WITH_STATUS =
            """{"serverTimeEpochMillis":1,"devices":[],"children":[{"childId":"k1","name":"Mia","status":{"day":"2026-01-05",""" +
                """"allowance":{"templateMinutes":60,"adjustmentMinutes":25,"totalMinutes":85},"usedMillis":2400000,""" +
                """"remainingMillis":2700000,"overrunMillis":0,"state":"READY"}}]}"""

        const val SNAPSHOT_WITHOUT_CHILDREN =
            """{"serverTimeEpochMillis":1,"devices":[{"id":"d1","name":"Handy","role":"PARENT","pairedAtEpochMillis":1}],"children":[]}"""
    }
}
