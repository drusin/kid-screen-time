package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.HistoryDay
import io.github.drusin.kidscreentime.api.HistoryResponse
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.api.UndoDailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.WeeklyTemplateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Ticket 16: der Verlauf. Die Uhr beginnt Montag 2026-01-05 09:00 Berlin (Wochenvorlage 60 min, Überziehungsgrenze
 * 60 min); Mitternacht Berlin ist 23:00 UTC.
 */
class HistoryApiTest {
    @TempDir
    lateinit var dir: Path

    private suspend fun HttpClient.history(token: String?, childId: String?): HttpResponse =
        get(TestServer.PUBLIC + ApiPaths.PARENT_HISTORY) {
            token?.let { header("Authorization", "Bearer $it") }
            childId?.let { parameter("childId", it) }
        }

    private suspend fun HttpClient.days(parent: RedeemPairingCodeResponse, childId: String): List<HistoryDay> {
        val response = history(parent.deviceToken, childId)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<HistoryResponse>().days
    }

    private suspend fun HttpClient.sync(device: RedeemPairingCodeResponse, vararg updates: SessionUpdate) =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(updates.toList()))
        }

    private suspend fun HttpClient.post(parent: RedeemPairingCodeResponse, path: String, body: Any) =
        post(TestServer.PUBLIC + path) {
            header("Authorization", "Bearer ${parent.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun minutes(n: Long) = Duration.ofMinutes(n).toMillis()

    private fun utc(text: String) = Instant.parse(text).toEpochMilli()

    @Test
    fun `Eine Sitzung über Mitternacht steht im Verlauf an beiden Tagen aufgeteilt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        server.clock.advance(Duration.ofHours(14) + Duration.ofMinutes(30)) // Montag 23:30
        val id = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(id, startAgeMillis = 0))
        server.clock.advance(Duration.ofMinutes(50)) // Dienstag 00:20
        client.sync(child, SessionUpdate(id, startAgeMillis = minutes(50), endAgeMillis = 0))

        val days = client.days(parent, child.childId!!)

        assertEquals(listOf("2026-01-06", "2026-01-05"), days.map { it.day })
        val (dienstag, montag) = days
        assertEquals(minutes(20), dienstag.usedMillis)
        assertEquals(minutes(30), montag.usedMillis)
        val abend = montag.sessions.single()
        assertEquals(utc("2026-01-05T22:30:00Z"), abend.startEpochMillis)
        assertEquals(utc("2026-01-05T23:00:00Z"), abend.endEpochMillis)
        assertTrue(abend.continuesNextDay)
        assertNull(abend.endReason)
        val nacht = dienstag.sessions.single()
        assertEquals(utc("2026-01-05T23:00:00Z"), nacht.startEpochMillis)
        assertEquals(utc("2026-01-05T23:20:00Z"), nacht.endEpochMillis)
        assertTrue(nacht.fromPreviousDay)
        assertEquals(SessionEndReason.STOPPED, nacht.endReason)
        assertFalse(nacht.recordedOffline)
    }

    @Test
    fun `Das Kontingent und die Überziehung vergangener Tage gelten nach der Vorlage von 00 00 dieses Tages`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val id = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(id, startAgeMillis = 0))
        server.clock.advance(Duration.ofMinutes(75)) // Montag 10:15, 15 min über 60
        client.sync(child, SessionUpdate(id, startAgeMillis = minutes(75), endAgeMillis = 0))
        // Montag: neue Vorlage 30 min gilt ab Dienstag. Ein Bonus am Dienstag zählt nur für den Dienstag.
        assertEquals(HttpStatusCode.OK, client.post(parent, ApiPaths.PARENT_WEEKLY_TEMPLATE, WeeklyTemplateRequest(child.childId!!, List(7) { 30 })).status)
        server.clock.advance(Duration.ofDays(1)) // Dienstag 10:15
        assertEquals(HttpStatusCode.OK, client.post(parent, ApiPaths.PARENT_DAILY_ADJUSTMENTS, DailyAdjustmentRequest(child.childId!!, 15)).status)
        server.clock.advance(Duration.ofDays(1)) // Mittwoch 10:15

        val (mittwoch, dienstag, montag) = client.days(parent, child.childId!!)

        assertEquals(30, mittwoch.allowance.totalMinutes)
        assertEquals(30, dienstag.allowance.templateMinutes)
        assertEquals(15, dienstag.allowance.adjustmentMinutes)
        assertEquals(45, dienstag.allowance.totalMinutes)
        assertEquals(60, montag.allowance.totalMinutes, "Montag rechnet mit der Vorlage, die um 00:00 galt")
        assertEquals(minutes(75), montag.usedMillis)
        assertEquals(minutes(15), montag.overrunMillis)
        assertEquals(0, dienstag.overrunMillis)
    }

    @Test
    fun `Eine zurückgenommene Tagesanpassung steht mit ihrer Gegenbuchung im Verlauf`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val childId = child.childId!!
        client.post(parent, ApiPaths.PARENT_DAILY_ADJUSTMENTS, DailyAdjustmentRequest(childId, 30))
        server.clock.advance(Duration.ofMinutes(5))
        client.post(parent, ApiPaths.PARENT_DAILY_ADJUSTMENTS, DailyAdjustmentRequest(childId, 10))
        server.clock.advance(Duration.ofMinutes(5))
        client.post(parent, ApiPaths.PARENT_DAILY_ADJUSTMENTS_UNDO, UndoDailyAdjustmentRequest(childId)) // nimmt +10 zurück

        val heute = client.days(parent, childId).single()

        assertEquals(listOf(30, 10, -10), heute.adjustments.map { it.deltaMinutes })
        assertEquals(listOf(false, true, false), heute.adjustments.map { it.takenBack })
        assertEquals(listOf(false, false, true), heute.adjustments.map { it.reversal })
        assertEquals(90, heute.allowance.totalMinutes)
    }

    @Test
    fun `Ein Automatischer Stopp des Servers und eine offline erfasste Sitzung sind im Verlauf markiert`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val automatic = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(automatic, startAgeMillis = 0))
        server.clock.advance(Duration.ofHours(2)) // 60 min Kontingent + 60 min Überziehung
        assertEquals(1, server.services.scheduler.runDue())
        server.clock.advance(Duration.ofHours(1))
        val offline = UUID.randomUUID().toString()
        // Das Tablet war offline: Start vor 30 min, Stopp vor 20 min, jetzt erst gemeldet.
        client.sync(child, SessionUpdate(offline, startAgeMillis = minutes(30), endAgeMillis = minutes(20)))

        val sessions = client.days(parent, child.childId!!).single().sessions

        val (a, o) = sessions
        assertEquals(automatic, a.id)
        assertEquals(SessionEndReason.AUTOMATIC_STOP, a.endReason)
        assertFalse(a.recordedOffline)
        assertEquals(offline, o.id)
        assertEquals(SessionEndReason.STOPPED, o.endReason)
        assertTrue(o.recordedOffline)
        assertEquals(minutes(10), o.durationMillis)
    }

    @Test
    fun `Ein Automatischer Stopp des Tablets bleibt markiert, auch wenn sein Sync vor dem Lauf des Servers kommt`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val withReason = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(withReason, startAgeMillis = 0))
        server.clock.advance(Duration.ofHours(2))
        // Das Tablet stoppt an der Grenze und synchronisiert sofort, der Server hat noch nichts gestoppt.
        client.sync(child, SessionUpdate(withReason, startAgeMillis = minutes(120), endAgeMillis = 0, endReason = SessionEndReason.AUTOMATIC_STOP))
        val withoutReason = UUID.randomUUID().toString()
        server.clock.advance(Duration.ofHours(1))
        client.sync(child, SessionUpdate(withoutReason, startAgeMillis = 0))
        server.clock.advance(Duration.ofMinutes(10))
        client.sync(child, SessionUpdate(withoutReason, startAgeMillis = minutes(10), endAgeMillis = 0))

        val sessions = client.days(parent, child.childId!!).single().sessions

        assertEquals(listOf(SessionEndReason.AUTOMATIC_STOP, SessionEndReason.STOPPED), sessions.map { it.endReason })
        assertEquals(0, server.services.scheduler.runDue(), "Der Server hat nichts mehr zu stoppen")
    }

    @Test
    fun `Die Startseite bekommt die letzten Tage ohne heute und nicht vor der Anlage des Kindes`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        val firstDay = client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.deviceToken}") }
            .body<ParentSnapshot>().children.single()
        assertTrue(firstDay.recentDays.isEmpty(), "Am Tag der Anlage gibt es noch keinen vergangenen Tag")
        val id = UUID.randomUUID().toString()
        client.sync(child, SessionUpdate(id, startAgeMillis = 0))
        server.clock.advance(Duration.ofMinutes(75))
        client.sync(child, SessionUpdate(id, startAgeMillis = minutes(75), endAgeMillis = 0))
        server.clock.advance(Duration.ofDays(2)) // Mittwoch

        val recent = client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.deviceToken}") }
            .body<ParentSnapshot>().children.single().recentDays

        assertEquals(listOf("2026-01-06", "2026-01-05"), recent.map { it.day })
        assertEquals(minutes(75), recent[1].usedMillis)
        assertEquals(minutes(15), recent[1].overrunMillis)
        assertEquals(60, recent[1].allowance.totalMinutes)
    }

    @Test
    fun `Die letzten Tage sind höchstens sieben und der volle Verlauf höchstens 90 Tage`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        server.clock.advance(Duration.ofDays(200))

        val recent = client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.deviceToken}") }
            .body<ParentSnapshot>().children.single().recentDays

        assertEquals(7, recent.size)
        assertEquals(90, client.days(parent, child.childId!!).size)
    }

    @Test
    fun `Der Verlauf braucht ein Elterngerät und ein bekanntes Kind`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairDevice(client, DeviceRole.CHILD)

        assertEquals(HttpStatusCode.Unauthorized, client.history(null, child.childId).status)
        assertEquals(HttpStatusCode.Unauthorized, client.history("falsches-token", child.childId).status)
        assertEquals(HttpStatusCode.Forbidden, client.history(child.deviceToken, child.childId).status)
        assertEquals(HttpStatusCode.BadRequest, client.history(parent.deviceToken, "unbekannt").status)
        assertEquals(HttpStatusCode.BadRequest, client.history(parent.deviceToken, null).status)
    }
}
