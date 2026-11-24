package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.api.UndoDailyAdjustmentRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/** Ticket 09: Tagesanpassung buchen und zurücknehmen (Gegenbuchung), Rollenprüfung, Folgen für Planer und Push. */
class DailyAdjustmentApiTest {
    @TempDir
    lateinit var dir: Path

    private val ntfy = FakeNtfy()

    @AfterTest
    fun stopNtfy() = ntfy.close()

    // Default clock: Monday 2026-01-05 09:00 in Berlin; Wochenvorlage 60 min.

    private suspend fun HttpClient.book(token: String?, childId: String, delta: Int): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_DAILY_ADJUSTMENTS) {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(DailyAdjustmentRequest(childId, delta))
        }

    private suspend fun HttpClient.undo(token: String?, childId: String): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_DAILY_ADJUSTMENTS_UNDO) {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(UndoDailyAdjustmentRequest(childId))
        }

    private suspend fun HttpResponse.allowance() = body<ParentSnapshot>().children.single().status!!.allowance

    private fun TestServer.adjustmentRows(): List<List<Any?>> = services.database.transaction { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT day, delta_minutes, booked_at, device_id, reverses_id FROM daily_adjustments ORDER BY id").use { rs ->
                buildList {
                    while (rs.next()) add(listOf(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getString(4), rs.getLong(5).takeUnless { rs.wasNull() }))
                }
            }
        }
    }

    private suspend fun TestServer.pairParentAndChild(client: HttpClient): Pair<RedeemPairingCodeResponse, RedeemPairingCodeResponse> =
        pairDevice(client, DeviceRole.PARENT) to pairDevice(client, DeviceRole.CHILD)

    @Test
    fun `Eine Bonuszeit wird mit Tag, Delta, Zeitpunkt und buchendem Gerät angehängt und liefert den neuen Eltern-Snapshot`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        val response = client.book(parent.deviceToken, child.childId!!, 30)

        assertEquals(HttpStatusCode.OK, response.status)
        val allowance = response.allowance()
        assertEquals(60, allowance.templateMinutes)
        assertEquals(30, allowance.adjustmentMinutes)
        assertEquals(90, allowance.totalMinutes)
        assertEquals(
            listOf(listOf("2026-01-05", 30, server.clock.now().toEpochMilli(), parent.deviceId, null)),
            server.adjustmentRows(),
        )
    }

    @Test
    fun `Mehrere Tagesanpassungen eines Tages addieren sich, das Kontingent fällt nie unter 0`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        client.book(parent.deviceToken, child.childId!!, 30)
        assertEquals(75, client.book(parent.deviceToken, child.childId!!, -15).allowance().totalMinutes)
        val overdrawn = client.book(parent.deviceToken, child.childId!!, -120).allowance()

        assertEquals(-105, overdrawn.adjustmentMinutes)
        assertEquals(0, overdrawn.totalMinutes)
    }

    @Test
    fun `Letzte zurücknehmen bucht eine Gegenbuchung und nimmt danach die vorletzte zurück`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        client.book(parent.deviceToken, child.childId!!, 30)
        client.book(parent.deviceToken, child.childId!!, -10)

        val first = client.undo(parent.deviceToken, child.childId!!)
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(30, first.body<ParentSnapshot>().children.single().lastAdjustmentMinutes, "Als nächstes wäre die +30 dran")
        assertEquals(
            listOf<List<Any?>>(listOf("2026-01-05", 30, 0L, parent.deviceId, null), listOf("2026-01-05", -10, 0L, parent.deviceId, null), listOf("2026-01-05", 10, 0L, parent.deviceId, 2L)),
            server.adjustmentRows().map { row -> row.toMutableList().also { it[2] = 0L } },
            "Nichts wird gelöscht, die Gegenbuchung zeigt auf das Original",
        )

        val second = client.undo(parent.deviceToken, child.childId!!)
        assertEquals(60, second.allowance().totalMinutes, "Beides ist zurückgenommen")
        assertNull(second.body<ParentSnapshot>().children.single().lastAdjustmentMinutes)
    }

    @Test
    fun `Ohne Tagesanpassung gibt es nichts zurückzunehmen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        assertEquals(HttpStatusCode.Conflict, client.undo(parent.deviceToken, child.childId!!).status)
        assertEquals(0, server.adjustmentRows().size)
    }

    @Test
    fun `Eine Tagesanpassung gilt nur für heute`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        client.book(parent.deviceToken, child.childId!!, 30)

        server.clock.advance(Duration.ofDays(1))
        val tomorrow = client.book(parent.deviceToken, child.childId!!, 5)

        assertEquals(5, tomorrow.allowance().adjustmentMinutes, "Die Anpassung von gestern zählt nicht mehr")
        assertEquals(listOf("2026-01-05", "2026-01-06"), server.adjustmentRows().map { it[0] })
    }

    @Test
    fun `Ungültige Anpassungen und unbekannte Kinder werden abgelehnt, ohne zu buchen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        assertEquals(HttpStatusCode.BadRequest, client.book(parent.deviceToken, child.childId!!, 0).status)
        assertEquals(HttpStatusCode.BadRequest, client.book(parent.deviceToken, child.childId!!, 24 * 60 + 1).status)
        assertEquals(HttpStatusCode.BadRequest, client.book(parent.deviceToken, "gibt-es-nicht", 5).status)
        assertEquals(HttpStatusCode.BadRequest, client.undo(parent.deviceToken, "gibt-es-nicht").status)
        assertEquals(0, server.adjustmentRows().size)
    }

    @Test
    fun `Rollenprüfung, 401 ohne Token und 403 für das Kindgerät, gebucht wird nichts`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (_, child) = server.pairParentAndChild(client)

        assertEquals(HttpStatusCode.Unauthorized, client.book(null, child.childId!!, 30).status)
        assertEquals(HttpStatusCode.Unauthorized, client.book("falsch", child.childId!!, 30).status)
        assertEquals(HttpStatusCode.Forbidden, client.book(child.deviceToken, child.childId!!, 30).status)
        assertEquals(HttpStatusCode.Unauthorized, client.undo(null, child.childId!!).status)
        assertEquals(HttpStatusCode.Forbidden, client.undo(child.deviceToken, child.childId!!).status)
        assertEquals(0, server.adjustmentRows().size)
    }

    @Test
    fun `Eine Tagesanpassung verschiebt den Automatischen Stopp, weil der Planer neu rechnet`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        val start = server.clock.now()
        client.post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${child.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(listOf(SessionUpdate(UUID.randomUUID().toString(), startAgeMillis = 0))))
        }
        assertEquals(start.plus(Duration.ofMinutes(120)), server.services.scheduler.nextDue())

        client.book(parent.deviceToken, child.childId!!, 30)
        assertEquals(start.plus(Duration.ofMinutes(150)), server.services.scheduler.nextDue(), "60 + 30 + 60 Überziehung")

        client.undo(parent.deviceToken, child.childId!!)
        assertEquals(start.plus(Duration.ofMinutes(120)), server.services.scheduler.nextDue())
    }

    @Test
    fun `Buchen und Zurücknehmen schicken ein Resync-Signal nur ans Kindgerät`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        val childReceiver = WebPushReceiver()
        val parentReceiver = WebPushReceiver()
        suspend fun register(token: String, topic: String, receiver: WebPushReceiver) = client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(PushRegistration("https://ntfy.example.org/$topic?up=1", receiver.publicKeyBase64Url, receiver.authSecretBase64Url))
        }
        register(child.deviceToken, "upKINDtopic0001", childReceiver)
        register(parent.deviceToken, "upELTERNtopic1", parentReceiver)

        client.book(parent.deviceToken, child.childId!!, 15)
        server.services.push.flush()
        client.undo(parent.deviceToken, child.childId!!)
        server.services.push.flush()

        assertEquals(listOf("/upKINDtopic0001?up=1", "/upKINDtopic0001?up=1"), ntfy.requests.map { it.pathAndQuery })
        ntfy.requests.forEach {
            assertEquals(PushSignal(), ApiJson.decodeFromString(PushSignal.serializer(), childReceiver.decrypt(it.body).decodeToString()))
        }
    }

    @Test
    fun `Eine abgelehnte Anpassung schickt kein Signal`() = testApplication {
        val server = TestServer(dir, ntfyInternalUrl = ntfy.url).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        val receiver = WebPushReceiver()
        client.post(TestServer.PUBLIC + ApiPaths.PUSH_REGISTRATION) {
            header("Authorization", "Bearer ${child.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(PushRegistration("https://ntfy.example.org/upKINDtopic0001?up=1", receiver.publicKeyBase64Url, receiver.authSecretBase64Url))
        }

        client.book(parent.deviceToken, child.childId!!, 0)
        client.undo(parent.deviceToken, child.childId!!)
        server.services.push.flush()

        assertEquals(0, ntfy.requests.size)
    }
}
