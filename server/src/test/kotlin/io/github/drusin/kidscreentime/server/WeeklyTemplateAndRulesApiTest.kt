package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.api.RulesRequest
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.api.WeeklyTemplateRequest
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
import org.junit.jupiter.api.io.TempDir

/** Ticket 10: Wochenvorlage (gilt ab morgen) und Regeln (Vorwarnschwelle, Überziehungsgrenze, Name), Rollenprüfung, Folgen. */
class WeeklyTemplateAndRulesApiTest {
    @TempDir
    lateinit var dir: Path

    private val ntfy = FakeNtfy()

    @AfterTest
    fun stopNtfy() = ntfy.close()

    // Default clock: Monday 2026-01-05 09:00 in Berlin; Wochenvorlage 60 min on weekdays, 120 on the weekend.

    private suspend fun HttpClient.setTemplate(token: String?, childId: String, minutes: List<Int>): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_WEEKLY_TEMPLATE) {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(WeeklyTemplateRequest(childId, minutes))
        }

    private suspend fun HttpClient.setRules(token: String?, childId: String, name: String = "Mia", warning: Int = 10, overrun: Int = 30): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_RULES) {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(RulesRequest(childId, name, warning, overrun))
        }

    private suspend fun HttpClient.childSnapshot(token: String): ChildSnapshot =
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(emptyList()))
        }.body()

    private fun TestServer.templateRows(): List<String> = services.database.transaction { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT valid_from, mon, tue, sun FROM weekly_template_versions ORDER BY valid_from").use { rs ->
                buildList { while (rs.next()) add("${rs.getString(1)}:${rs.getInt(2)}/${rs.getInt(3)}/${rs.getInt(4)}") }
            }
        }
    }

    private suspend fun TestServer.pairParentAndChild(client: HttpClient) = pairDevice(client, DeviceRole.PARENT) to pairDevice(client, DeviceRole.CHILD)

    private val newWeek = listOf(30, 45, 60, 60, 60, 90, 150)

    @Test
    fun `Eine neue Wochenvorlage ist eine Version ab morgen, das heutige Kontingent bleibt, morgen gilt der neue Wert`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        val response = client.setTemplate(parent.deviceToken, child.childId!!, newWeek)

        assertEquals(HttpStatusCode.OK, response.status)
        val kid = response.body<ParentSnapshot>().children.single()
        assertEquals(60, kid.status!!.allowance.totalMinutes, "Heute (Montag) bleibt es bei 60")
        assertEquals(60, kid.weeklyTemplate!!.minutes[0])
        assertEquals("2026-01-06", kid.weeklyTemplateTomorrow!!.validFrom)
        assertEquals(newWeek, kid.weeklyTemplateTomorrow!!.minutes)
        assertEquals(listOf("2026-01-05:60/60/120", "2026-01-06:30/45/150"), server.templateRows(), "Die alte Version bleibt erhalten")

        server.clock.advance(Duration.ofDays(1))
        assertEquals(45, client.childSnapshot(child.deviceToken).status.allowance.totalMinutes, "Dienstag: neuer Wert, ohne Job")
    }

    @Test
    fun `Eine zweite Änderung am selben Tag ersetzt die Version von morgen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        client.setTemplate(parent.deviceToken, child.childId!!, newWeek)
        client.setTemplate(parent.deviceToken, child.childId!!, List(7) { 20 })

        assertEquals(listOf("2026-01-05:60/60/120", "2026-01-06:20/20/20"), server.templateRows())
    }

    @Test
    fun `Ungültige Vorlagen und unbekannte Kinder werden abgelehnt, ohne zu speichern`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        assertEquals(HttpStatusCode.BadRequest, client.setTemplate(parent.deviceToken, child.childId!!, listOf(60, 60)).status)
        assertEquals(HttpStatusCode.BadRequest, client.setTemplate(parent.deviceToken, child.childId!!, newWeek.dropLast(1) + -5).status)
        assertEquals(HttpStatusCode.BadRequest, client.setTemplate(parent.deviceToken, child.childId!!, newWeek.dropLast(1) + 24 * 60 + 1).status)
        assertEquals(HttpStatusCode.BadRequest, client.setTemplate(parent.deviceToken, "gibt-es-nicht", newWeek).status)
        assertEquals(listOf("2026-01-05:60/60/120"), server.templateRows())
    }

    @Test
    fun `Regeln setzen ändert Namen, Vorwarnschwelle und Überziehungsgrenze, das Kindgerät bekommt sie im Snapshot`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)

        val response = client.setRules(parent.deviceToken, child.childId!!, name = "  Mia  ", warning = 10, overrun = 30)

        assertEquals(HttpStatusCode.OK, response.status)
        val kid = response.body<ParentSnapshot>().children.single()
        assertEquals("Mia", kid.name)
        assertEquals(10, kid.warningThresholdMinutes)
        assertEquals(30, kid.overrunLimitMinutes)
        val onChild = client.childSnapshot(child.deviceToken)
        assertEquals(listOf("Mia", 10, 30), listOf(onChild.childName, onChild.warningThresholdMinutes, onChild.overrunLimitMinutes))
    }

    @Test
    fun `Ungültige Regeln werden abgelehnt, ohne zu speichern`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        val before = client.childSnapshot(child.deviceToken)

        assertEquals(HttpStatusCode.BadRequest, client.setRules(parent.deviceToken, child.childId!!, name = "  ").status)
        assertEquals(HttpStatusCode.BadRequest, client.setRules(parent.deviceToken, child.childId!!, warning = -1).status)
        assertEquals(HttpStatusCode.BadRequest, client.setRules(parent.deviceToken, child.childId!!, overrun = 24 * 60 + 1).status)
        assertEquals(HttpStatusCode.BadRequest, client.setRules(parent.deviceToken, "gibt-es-nicht").status)
        val after = client.childSnapshot(child.deviceToken)
        assertEquals(listOf(before.childName, before.warningThresholdMinutes, before.overrunLimitMinutes), listOf(after.childName, after.warningThresholdMinutes, after.overrunLimitMinutes))
    }

    @Test
    fun `Rollenprüfung, 401 ohne Token und 403 für das Kindgerät, gespeichert wird nichts`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (_, child) = server.pairParentAndChild(client)

        assertEquals(HttpStatusCode.Unauthorized, client.setTemplate(null, child.childId!!, newWeek).status)
        assertEquals(HttpStatusCode.Unauthorized, client.setTemplate("falsch", child.childId!!, newWeek).status)
        assertEquals(HttpStatusCode.Forbidden, client.setTemplate(child.deviceToken, child.childId!!, newWeek).status)
        assertEquals(HttpStatusCode.Unauthorized, client.setRules(null, child.childId!!).status)
        assertEquals(HttpStatusCode.Forbidden, client.setRules(child.deviceToken, child.childId!!).status)
        assertEquals(listOf("2026-01-05:60/60/120"), server.templateRows())
        assertEquals(5, client.childSnapshot(child.deviceToken).warningThresholdMinutes)
    }

    @Test
    fun `Eine neue Überziehungsgrenze verschiebt den Automatischen Stopp, weil der Planer neu rechnet`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val (parent, child) = server.pairParentAndChild(client)
        val start = server.clock.now()
        client.post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${child.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(listOf(SessionUpdate(UUID.randomUUID().toString(), startAgeMillis = 0))))
        }
        assertEquals(start.plus(Duration.ofMinutes(120)), server.services.automaticStops.nextDue(child.childId!!, server.clock.now()))

        client.setRules(parent.deviceToken, child.childId!!, overrun = 30)

        assertEquals(start.plus(Duration.ofMinutes(90)), server.services.automaticStops.nextDue(child.childId!!, server.clock.now()), "60 + 30 Überziehung")
    }

    @Test
    fun `Vorlage und Regeln schicken ein Resync-Signal nur ans Kindgerät, eine abgelehnte Aktion keins`() = testApplication {
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

        client.setTemplate(parent.deviceToken, child.childId!!, listOf(1))
        client.setRules(parent.deviceToken, child.childId!!, name = "")
        server.services.push.flush()
        assertEquals(0, ntfy.requests.size)

        client.setTemplate(parent.deviceToken, child.childId!!, newWeek)
        server.services.push.flush()
        client.setRules(parent.deviceToken, child.childId!!)
        server.services.push.flush()

        assertEquals(listOf("/upKINDtopic0001?up=1", "/upKINDtopic0001?up=1"), ntfy.requests.map { it.pathAndQuery })
        ntfy.requests.forEach {
            assertEquals(PushSignal(), ApiJson.decodeFromString(PushSignal.serializer(), childReceiver.decrypt(it.body).decodeToString()))
        }
    }
}
