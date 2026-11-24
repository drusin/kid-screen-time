package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** Ticket 04: Kindgerät koppeln (Rolle, Kind anlegen, Ersetzen-Hinweis), Rollenprüfung und beide Snapshots. */
class ChildDeviceApiTest {
    @TempDir
    lateinit var dir: Path

    // Default clock: Monday 2026-01-05 09:00 in Berlin.
    private val minute = 60_000L

    private suspend fun HttpClient.createCode(parent: RedeemPairingCodeResponse, request: CreatePairingCodeRequest): HttpResponse =
        post(TestServer.PUBLIC + ApiPaths.PARENT_PAIRING_CODES) {
            header("Authorization", "Bearer ${parent.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private suspend fun HttpClient.getAs(token: String?, path: String): HttpResponse =
        get(TestServer.PUBLIC + path) { token?.let { header("Authorization", "Bearer $it") } }

    private suspend fun HttpClient.parentSnapshot(parent: RedeemPairingCodeResponse) =
        getAs(parent.deviceToken, ApiPaths.PARENT_SNAPSHOT).body<ParentSnapshot>()

    private suspend fun HttpClient.childSnapshot(child: RedeemPairingCodeResponse) =
        getAs(child.deviceToken, ApiPaths.CHILD_SNAPSHOT).body<ChildSnapshot>()

    /** The Elterngerät creates the code for a new Kind and the tablet redeems it. */
    private suspend fun TestServer.pairChildDevice(client: HttpClient, parent: RedeemPairingCodeResponse, kind: String = "Mia"): RedeemPairingCodeResponse {
        val created = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, newChildName = kind)).body<CreatePairingCodeResponse>()
        return client.redeem(created.code, "Tablet").body()
    }

    @Test
    fun `Elterngerät erzeugt einen Kopplungscode für ein Kindgerät, das Kind entsteht beim Einlösen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT, "Handy")

        val response = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, newChildName = " Mia "))

        assertEquals(HttpStatusCode.OK, response.status)
        val created = response.body<CreatePairingCodeResponse>()
        assertEquals(DeviceRole.CHILD, created.role)
        assertEquals("Mia", created.childName)
        assertEquals("https://kst.example.org", created.serverUrl)
        assertEquals(server.clock.now().toEpochMilli(), created.serverTimeEpochMillis)
        assertEquals(server.clock.now().plus(Duration.ofMinutes(10)).toEpochMilli(), created.expiresAtEpochMillis)
        assertEquals(0, server.services.children.count(), "Das Kind entsteht erst beim Einlösen")

        val paired = client.redeem(created.code, "Tablet").body<RedeemPairingCodeResponse>()

        assertEquals(DeviceRole.CHILD, paired.role)
        assertEquals("Mia", paired.childName)
        assertNotNull(paired.childId)
        assertEquals(1, server.services.children.count())
    }

    @Test
    fun `Ein neues Kind bekommt Wochenvorlage mit Standardwerten, 5 min Vorwarnschwelle und 60 min Überziehungsgrenze`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)

        val snapshot = client.childSnapshot(child)

        assertEquals(5, snapshot.warningThresholdMinutes)
        assertEquals(60, snapshot.overrunLimitMinutes)
        assertEquals(listOf(60, 60, 60, 60, 60, 120, 120), snapshot.templateToday.minutes)
        assertEquals("2026-01-05", snapshot.templateToday.validFrom)
    }

    @Test
    fun `Der Kind-Snapshot rechnet mit domain Tageskontingent, Verbrauch, Restzeit und laufende Sitzung`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)
        val now = server.clock.now().toEpochMilli()
        server.services.database.transaction { c ->
            c.createStatement().use {
                // 20 min earlier a finished 10 min Sitzung, a Sitzung running since 15 min, and a +25 min Tagesanpassung.
                it.execute("INSERT INTO sessions (id, child_id, start_at, end_at, end_reason) VALUES ('s1','${child.childId}',${now - 60 * minute},${now - 50 * minute},'STOPPED')")
                it.execute("INSERT INTO sessions (id, child_id, start_at) VALUES ('s2','${child.childId}',${now - 15 * minute})")
                it.execute("INSERT INTO daily_adjustments (child_id, day, delta_minutes, booked_at) VALUES ('${child.childId}','2026-01-05',25,${now - 30 * minute})")
                it.execute("INSERT INTO daily_adjustments (child_id, day, delta_minutes, booked_at) VALUES ('${child.childId}','2026-01-04',99,${now - 30 * minute})")
            }
        }

        val snapshot = client.childSnapshot(child)

        assertEquals("Mia", snapshot.childName)
        assertEquals(now, snapshot.serverTimeEpochMillis)
        assertEquals("Europe/Berlin", snapshot.timeZone)
        val status = snapshot.status
        assertEquals("2026-01-05", status.day)
        assertEquals(60, status.allowance.templateMinutes)
        assertEquals(25, status.allowance.adjustmentMinutes)
        assertEquals(85, status.allowance.totalMinutes)
        assertEquals(25 * minute, status.usedMillis)
        assertEquals(60 * minute, status.remainingMillis)
        assertEquals(0, status.overrunMillis)
        assertEquals(ChildState.RUNNING, status.state)
        assertEquals("s2", status.runningSession?.id)
        assertEquals(now - 15 * minute, status.runningSession?.startEpochMillis)
        assertEquals(listOf("s1", "s2"), snapshot.todaySessions.map { it.id })
        assertEquals(listOf(25), snapshot.todayAdjustments.map { it.deltaMinutes })
    }

    @Test
    fun `Der Kind-Snapshot enthält die gültige Wochenvorlage für heute und morgen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)
        server.services.database.transaction { c ->
            c.createStatement().use {
                it.execute("INSERT INTO weekly_template_versions VALUES ('${child.childId}','2026-01-06',30,30,30,30,30,30,30)")
            }
        }

        val snapshot = client.childSnapshot(child)

        assertEquals("2026-01-05", snapshot.templateToday.validFrom)
        assertEquals(60, snapshot.templateToday.minutes[0])
        assertEquals("2026-01-06", snapshot.templateTomorrow.validFrom)
        assertEquals(List(7) { 30 }, snapshot.templateTomorrow.minutes)
        assertEquals(60, snapshot.status.allowance.templateMinutes, "Die Änderung gilt erst ab morgen")
    }

    @Test
    fun `Am Wochenende ist das Standard-Tageskontingent 120 Minuten`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)

        server.clock.advance(Duration.ofDays(5)) // Saturday

        assertEquals(120, client.childSnapshot(child).status.allowance.totalMinutes)
        assertEquals(ChildState.READY, client.childSnapshot(child).status.state)
    }

    @Test
    fun `Der Eltern-Snapshot zeigt denselben Stand wie das Kindgerät`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)
        val now = server.clock.now().toEpochMilli()
        server.services.database.transaction { c ->
            c.createStatement().use {
                it.execute("INSERT INTO sessions (id, child_id, start_at, end_at, end_reason) VALUES ('s1','${child.childId}',${now - 45 * minute},${now - 30 * minute},'STOPPED')")
            }
        }

        val forParent = client.parentSnapshot(parent)

        val kind = forParent.children.single()
        assertEquals(child.childId, kind.childId)
        assertEquals("Mia", kind.name)
        assertEquals(client.childSnapshot(child).status, kind.status)
        assertEquals(15 * minute, kind.status?.usedMillis)
        assertEquals(45 * minute, kind.status?.remainingMillis)
        assertEquals(5, kind.warningThresholdMinutes)
        assertEquals(60, kind.overrunLimitMinutes)
        assertEquals(listOf(60, 60, 60, 60, 60, 120, 120), kind.weeklyTemplate?.minutes)
        assertEquals(setOf(DeviceRole.PARENT, DeviceRole.CHILD), forParent.devices.map { it.role }.toSet())
        assertEquals(child.childId, forParent.devices.single { it.role == DeviceRole.CHILD }.childId)
    }

    @Test
    fun `Ein Kindgerät-Token bekommt bei Eltern-Endpunkten 403`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)

        assertEquals(HttpStatusCode.Forbidden, client.getAs(child.deviceToken, ApiPaths.PARENT_SNAPSHOT).status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.createCode(child, CreatePairingCodeRequest(DeviceRole.PARENT)).status,
        )
    }

    @Test
    fun `Ein Elterngerät-Token bekommt am Kind-Snapshot 403`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)

        assertEquals(HttpStatusCode.Forbidden, client.getAs(parent.deviceToken, ApiPaths.CHILD_SNAPSHOT).status)
    }

    @Test
    fun `Ohne gültiges Token antworten die neuen Endpunkte mit 401`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)

        assertEquals(HttpStatusCode.Unauthorized, client.getAs(null, ApiPaths.CHILD_SNAPSHOT).status)
        assertEquals(HttpStatusCode.Unauthorized, client.getAs("falsch", ApiPaths.CHILD_SNAPSHOT).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post(TestServer.PUBLIC + ApiPaths.PARENT_PAIRING_CODES) {
                contentType(ContentType.Application.Json)
                setBody(CreatePairingCodeRequest(DeviceRole.PARENT))
            }.status,
        )
        server.services.database.transaction { c ->
            c.createStatement().use { it.execute("UPDATE devices SET unpaired_at = 1 WHERE id = '${child.deviceId}'") }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.getAs(child.deviceToken, ApiPaths.CHILD_SNAPSHOT).status)
    }

    @Test
    fun `Hat das Kind schon ein Kindgerät, nennt die Antwort den Ersetzen-Hinweis`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)

        val created = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = child.childId))
            .body<CreatePairingCodeResponse>()

        assertEquals("Tablet", created.replacesDeviceName)
        assertEquals("Mia", created.childName)
        assertEquals(child.childId, created.childId)
        assertEquals(2, created.snapshot.devices.size)
    }

    @Test
    fun `Ohne Kindgerät gibt es keinen Ersetzen-Hinweis und das zweite Gerät koppelt an dasselbe Kind`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)
        server.services.database.transaction { c ->
            c.createStatement().use { it.execute("UPDATE devices SET unpaired_at = 1 WHERE id = '${child.deviceId}'") }
        }

        val created = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = child.childId))
            .body<CreatePairingCodeResponse>()
        val paired = client.redeem(created.code, "Neues Tablet").body<RedeemPairingCodeResponse>()

        assertNull(created.replacesDeviceName)
        assertEquals(child.childId, paired.childId)
        assertEquals(1, server.services.children.count())
    }

    @Test
    fun `Solange das Ersetzen fehlt, lehnt das Einlösen für ein Kind mit Kindgerät ab und der Code bleibt gültig`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)
        val child = server.pairChildDevice(client, parent)
        val created = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.CHILD, childId = child.childId))
            .body<CreatePairingCodeResponse>()

        assertEquals(HttpStatusCode.Conflict, client.redeem(created.code, "Zweites Tablet").status)

        assertEquals(HttpStatusCode.OK, client.getAs(child.deviceToken, ApiPaths.CHILD_SNAPSHOT).status)
        assertEquals(2, client.parentSnapshot(parent).devices.size)
    }

    @Test
    fun `Ungültige Wünsche für einen Kopplungscode antworten mit 400`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)

        suspend fun status(request: CreatePairingCodeRequest) = client.createCode(parent, request).status

        assertEquals(HttpStatusCode.BadRequest, status(CreatePairingCodeRequest(DeviceRole.CHILD)), "weder Kind noch Name")
        assertEquals(HttpStatusCode.BadRequest, status(CreatePairingCodeRequest(DeviceRole.CHILD, newChildName = "  ")), "leerer Name")
        assertEquals(HttpStatusCode.BadRequest, status(CreatePairingCodeRequest(DeviceRole.CHILD, childId = "gibt-es-nicht")), "unbekanntes Kind")
        assertEquals(HttpStatusCode.BadRequest, status(CreatePairingCodeRequest(DeviceRole.PARENT, newChildName = "Mia")), "Elterngerät mit Kind")

        val child = server.pairChildDevice(client, parent)
        assertEquals(
            HttpStatusCode.BadRequest,
            status(CreatePairingCodeRequest(DeviceRole.CHILD, newChildName = "Zweites Kind")),
            "es gibt schon ein Kind",
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status(CreatePairingCodeRequest(DeviceRole.CHILD, childId = child.childId, newChildName = "Mia")),
            "beides",
        )
    }

    @Test
    fun `Ein Elterngerät kann auch für ein weiteres Elterngerät einen Code erzeugen`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)

        val created = client.createCode(parent, CreatePairingCodeRequest(DeviceRole.PARENT)).body<CreatePairingCodeResponse>()
        val second = client.redeem(created.code, "Handy von Papa").body<RedeemPairingCodeResponse>()

        assertEquals(DeviceRole.PARENT, second.role)
        assertNull(second.childId)
        assertTrue(created.snapshot.children.isEmpty())
    }
}
