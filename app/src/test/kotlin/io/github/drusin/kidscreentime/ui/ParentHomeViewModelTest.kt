package io.github.drusin.kidscreentime.ui

import io.github.drusin.kidscreentime.MainDispatcherRule
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.ui.parent.ParentHomeViewModel
import io.github.drusin.kidscreentime.ui.parent.usedFraction
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
class ParentHomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = TestContainer()

    @Before
    fun setUp() {
        app.handler = { request ->
            when (request.url.encodedPath) {
                ApiPaths.PAIRING_REDEEM -> respondJson("""{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}""")
                else -> respondJson(SNAPSHOT_WITHOUT_CHILDREN)
            }
        }
        runBlocking { app.container.pairingRepository.pair("https://kst.example.org", "ABCD-EFGH", "Handy") }
        app.requests.clear()
    }


    @Test
    fun `Ohne gekoppeltes Kind zeigt die Startseite den Hinweis`() = runTest {
        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading }

        assertTrue(viewModel.state.value.showNoChildHint)
        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.offline)
    }

    @Test
    fun `Der Eltern-Snapshot wird mit dem Geräte-Token als Bearer-Header geholt`() = runTest {
        main.track(ParentHomeViewModel(app.container.parentRepository)).state.await { !it.loading }

        val request = app.requests.single()
        assertEquals("https://kst.example.org" + ApiPaths.PARENT_SNAPSHOT, request.url.toString())
        assertEquals("Bearer tok-123", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `Ist der Server nicht erreichbar, bleibt der Hinweis und die Startseite meldet es`() = runTest {
        app.handler = { throw IOException("offline") }

        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading }

        assertTrue(viewModel.state.value.offline)
        assertTrue(viewModel.state.value.showNoChildHint)
    }

    @Test
    fun `Eine Antwort 401 entkoppelt das Gerät in der App und gilt nicht als offline`() = runTest {
        app.handler = { respond("", HttpStatusCode.Unauthorized) }

        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading }

        assertFalse(viewModel.state.value.offline)
        assertEquals(io.github.drusin.kidscreentime.pairing.DeviceState.Revoked, app.container.pairingRepository.state.value)
    }

    @Test
    fun `Die Statuskarte bekommt Restzeit, Verbrauch und Aufschlüsselung des Kindes vom Server`() = runTest {
        app.handler = { respondJson(SNAPSHOT_WITH_STATUS) }

        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
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

    private fun snapshotWith(adjustment: Int, last: Int? = null): String {
        val total = (60 + adjustment).coerceAtLeast(0)
        return """{"serverTimeEpochMillis":1,"devices":[],"children":[{"childId":"k1","name":"Mia","status":{"day":"2026-01-05",""" +
            """"allowance":{"templateMinutes":60,"adjustmentMinutes":$adjustment,"totalMinutes":$total},"usedMillis":0,""" +
            """"remainingMillis":${total * 60_000L},"overrunMillis":0,"state":"READY"}${last?.let { ""","lastAdjustmentMinutes":$it""" } ?: ""}}]}"""
    }

    private fun bodyOf(request: io.ktor.client.request.HttpRequestData) =
        (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private suspend fun viewModelWith(adjustment: Int = 0, last: Int? = null): ParentHomeViewModel {
        app.handler = { respondJson(snapshotWith(adjustment, last)) }
        return main.track(ParentHomeViewModel(app.container.parentRepository)).also { vm -> vm.state.await { !it.loading } }
    }

    @Test
    fun `Der Stepper bereitet in 5-Minuten-Schritten vor, die Vorschau zeigt das neue Tageskontingent`() = runTest {
        val viewModel = viewModelWith()

        viewModel.stepAdjustment(5)
        viewModel.stepAdjustment(5)
        viewModel.stepAdjustment(5)

        assertEquals(15, viewModel.state.value.pendingAdjustment)
        assertEquals(75, viewModel.state.value.previewAllowanceMinutes)
        viewModel.stepAdjustment(-5)
        assertEquals(70, viewModel.state.value.previewAllowanceMinutes)
        assertTrue(app.requests.none { it.method.value == "POST" }, "Vorbereiten bucht nichts")
    }

    @Test
    fun `Die Vorschau fällt nie unter 0, auch nicht nach früheren Anpassungen`() = runTest {
        val viewModel = viewModelWith(adjustment = -50)

        repeat(5) { viewModel.stepAdjustment(-5) }

        assertEquals(-25, viewModel.state.value.pendingAdjustment)
        assertEquals(0, viewModel.state.value.previewAllowanceMinutes, "60 − 50 − 25 liegt unter 0")
    }

    @Test
    fun `Gebucht wird erst auf Knopfdruck, und ohne vorbereiteten Wert gar nicht`() = runTest {
        val viewModel = viewModelWith()
        assertFalse(viewModel.state.value.canBook)

        viewModel.bookAdjustment()

        assertTrue(app.requests.none { it.method.value == "POST" })
    }

    @Test
    fun `Buchen schickt Kind und Delta, übernimmt den neuen Eltern-Snapshot und setzt den Stepper zurück`() = runTest {
        val viewModel = viewModelWith()
        app.handler = { request ->
            if (request.url.encodedPath == ApiPaths.PARENT_DAILY_ADJUSTMENTS) respondJson(snapshotWith(30, last = 30)) else respondJson(snapshotWith(0))
        }
        viewModel.stepAdjustment(30)

        viewModel.bookAdjustment()
        viewModel.state.await { it.child?.status?.allowance?.adjustmentMinutes == 30 }

        val post = app.requests.last { it.method.value == "POST" }
        assertEquals("https://kst.example.org" + ApiPaths.PARENT_DAILY_ADJUSTMENTS, post.url.toString())
        assertEquals("Bearer tok-123", post.headers[HttpHeaders.Authorization])
        assertEquals("""{"childId":"k1","deltaMinutes":30}""", bodyOf(post))
        val state = viewModel.state.value
        assertEquals(0, state.pendingAdjustment)
        assertEquals(90, state.child?.status?.allowance?.totalMinutes)
        assertEquals(30, state.undoableAdjustmentMinutes)
        assertFalse(state.acting)
        assertEquals(1, app.requests.count { it.method.value == "POST" }, "Der Snapshot der Antwort ist der Abgleich, kein zweiter Aufruf")
    }

    @Test
    fun `Letzte zurücknehmen schickt die Gegenbuchung-Aktion und zeigt den neuen Stand`() = runTest {
        val viewModel = viewModelWith(adjustment = 30, last = 30)
        app.handler = { request ->
            if (request.url.encodedPath == ApiPaths.PARENT_DAILY_ADJUSTMENTS_UNDO) respondJson(snapshotWith(0)) else respondJson(snapshotWith(30, last = 30))
        }
        assertEquals(30, viewModel.state.value.undoableAdjustmentMinutes)

        viewModel.undoLastAdjustment()
        viewModel.state.await { it.child?.status?.allowance?.adjustmentMinutes == 0 }

        assertEquals("""{"childId":"k1"}""", bodyOf(app.requests.last { it.method.value == "POST" }))
        assertEquals(null, viewModel.state.value.undoableAdjustmentMinutes)
    }

    @Test
    fun `Ohne Anpassung heute gibt es nichts zurückzunehmen`() = runTest {
        val viewModel = viewModelWith()

        viewModel.undoLastAdjustment()

        assertTrue(app.requests.none { it.method.value == "POST" })
    }

    @Test
    fun `Scheitert das Buchen, bleibt der vorbereitete Wert und die Startseite meldet es`() = runTest {
        val viewModel = viewModelWith()
        app.handler = { throw IOException("offline") }
        viewModel.stepAdjustment(10)

        viewModel.bookAdjustment()
        viewModel.state.await { it.actionFailed }

        assertEquals(10, viewModel.state.value.pendingAdjustment)
        assertFalse(viewModel.state.value.acting)
        viewModel.stepAdjustment(5)
        assertFalse(viewModel.state.value.actionFailed, "Eine neue Eingabe nimmt die Meldung weg")
    }

    private fun snapshotWithRules(name: String, warning: Int, overrun: Int, tomorrow: String) =
        """{"serverTimeEpochMillis":1,"devices":[],"children":[{"childId":"k1","name":"$name","warningThresholdMinutes":$warning,""" +
            """"overrunLimitMinutes":$overrun,"weeklyTemplate":{"validFrom":"2026-01-01","minutes":[60,60,60,60,60,120,120]},""" +
            """"weeklyTemplateTomorrow":{"validFrom":"2026-01-06","minutes":$tomorrow}}]}"""

    @Test
    fun `Wochenvorlage speichern schickt sieben Werte und übernimmt den Snapshot der Antwort`() = runTest {
        val viewModel = viewModelWith()
        app.handler = { request ->
            if (request.url.encodedPath == ApiPaths.PARENT_WEEKLY_TEMPLATE) respondJson(snapshotWithRules("Mia", 5, 60, "[30,45,60,60,60,90,150]"))
            else respondJson(snapshotWith(0))
        }
        var done = false

        viewModel.saveWeeklyTemplate(listOf(30, 45, 60, 60, 60, 90, 150)) { done = true }
        viewModel.state.await { it.child?.weeklyTemplateTomorrow != null }

        val post = app.requests.last { it.method.value == "POST" }
        assertEquals("https://kst.example.org" + ApiPaths.PARENT_WEEKLY_TEMPLATE, post.url.toString())
        assertEquals("""{"childId":"k1","minutes":[30,45,60,60,60,90,150]}""", bodyOf(post))
        assertEquals(listOf(30, 45, 60, 60, 60, 90, 150), viewModel.state.value.child?.weeklyTemplateTomorrow?.minutes)
        viewModel.state.await { !it.acting }
        assertTrue(done, "Der Dialog schließt, wenn der Server zugestimmt hat")
        assertEquals(1, app.requests.count { it.method.value == "POST" }, "Der Snapshot der Antwort ist der Abgleich")
    }

    @Test
    fun `Eine Wochenvorlage mit falscher Anzahl oder unmöglichen Werten wird gar nicht erst geschickt`() = runTest {
        val viewModel = viewModelWith()

        viewModel.saveWeeklyTemplate(listOf(60, 60))
        viewModel.saveWeeklyTemplate(listOf(60, 60, 60, 60, 60, 60, -5))
        viewModel.saveRules("  ", 5, 60)
        viewModel.saveRules("Mia", -5, 60)

        assertTrue(app.requests.none { it.method.value == "POST" })
    }

    @Test
    fun `Regeln speichern schickt Name, Vorwarnschwelle und Überziehungsgrenze und zeigt den neuen Stand`() = runTest {
        val viewModel = viewModelWith()
        app.handler = { request ->
            if (request.url.encodedPath == ApiPaths.PARENT_RULES) respondJson(snapshotWithRules("Mimi", 10, 30, "[60,60,60,60,60,120,120]"))
            else respondJson(snapshotWith(0))
        }

        viewModel.saveRules(" Mimi ", 10, 30)
        viewModel.state.await { it.child?.name == "Mimi" }

        assertEquals("""{"childId":"k1","name":"Mimi","warningThresholdMinutes":10,"overrunLimitMinutes":30}""", bodyOf(app.requests.last { it.method.value == "POST" }))
        assertEquals(10, viewModel.state.value.child?.warningThresholdMinutes)
        assertEquals(30, viewModel.state.value.child?.overrunLimitMinutes)
    }

    @Test
    fun `Scheitert das Speichern der Regeln, bleibt der Dialog offen und meldet es`() = runTest {
        val viewModel = viewModelWith()
        app.handler = { throw IOException("offline") }
        var done = false

        viewModel.saveRules("Mia", 10, 30) { done = true }
        viewModel.state.await { it.actionFailed }

        assertFalse(done)
        viewModel.clearActionFailed()
        assertFalse(viewModel.state.value.actionFailed)
    }

    private fun devicesSnapshot(vararg devices: String) =
        """{"serverTimeEpochMillis":1,"devices":[${devices.joinToString(",")}],"children":[]}"""

    private fun device(id: String, role: String, name: String = id) =
        """{"id":"$id","name":"$name","role":"$role","pairedAtEpochMillis":1}"""

    @Test
    fun `Die Geräteliste kommt aus dem Eltern-Snapshot, das letzte Elterngerät hat keinen Entkoppeln-Knopf`() = runTest {
        app.handler = { respondJson(devicesSnapshot(device("d1", "PARENT"), device("k1", "CHILD"))) }

        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        val state = viewModel.state.await { !it.loading }

        assertEquals(listOf("d1", "k1"), state.devices.map { it.id })
        assertEquals("d1", state.ownDeviceId)
        assertFalse(state.canUnpair(state.devices.single { it.id == "d1" }), "letztes Elterngerät")
        assertTrue(state.canUnpair(state.devices.single { it.id == "k1" }), "ein Kindgerät geht immer")
    }

    @Test
    fun `Mit zwei Elterngeräten lässt sich jedes entkoppeln, auch das eigene, und die Antwort ist die neue Liste`() = runTest {
        app.handler = { respondJson(devicesSnapshot(device("d1", "PARENT"), device("d2", "PARENT"))) }
        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading }
        assertTrue(viewModel.state.value.devices.all { viewModel.state.value.canUnpair(it) })
        app.handler = { request ->
            if (request.url.encodedPath == ApiPaths.PARENT_UNPAIR_DEVICE) respondJson(devicesSnapshot(device("d1", "PARENT")))
            else respondJson(devicesSnapshot(device("d1", "PARENT"), device("d2", "PARENT")))
        }

        viewModel.unpairDevice("d2")
        viewModel.state.await { it.devices.size == 1 }

        assertEquals("""{"deviceId":"d2"}""", bodyOf(app.requests.last { it.method.value == "POST" }))
        assertFalse(viewModel.state.value.actionFailed)
        assertIs<io.github.drusin.kidscreentime.pairing.DeviceState.Paired>(app.container.pairingRepository.state.value)
    }

    @Test
    fun `Ein Entkoppeln des letzten Elterngeräts wird gar nicht erst geschickt`() = runTest {
        app.handler = { respondJson(devicesSnapshot(device("d1", "PARENT"))) }
        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading }
        app.requests.clear()

        viewModel.unpairDevice("d1")

        assertTrue(app.requests.none { it.url.encodedPath == ApiPaths.PARENT_UNPAIR_DEVICE })
    }

    @Test
    fun `Das eigene Gerät zu entkoppeln beendet die Kopplung dieser App`() = runTest {
        app.handler = { respondJson(devicesSnapshot(device("d1", "PARENT"), device("d2", "PARENT"))) }
        val viewModel = main.track(ParentHomeViewModel(app.container.parentRepository))
        viewModel.state.await { !it.loading }
        app.handler = { respondJson(devicesSnapshot(device("d2", "PARENT"))) }

        viewModel.unpairDevice("d1")
        viewModel.state.await { !it.acting && it.devices.size == 1 }

        assertEquals(io.github.drusin.kidscreentime.pairing.DeviceState.Revoked, app.container.pairingRepository.state.value)
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
