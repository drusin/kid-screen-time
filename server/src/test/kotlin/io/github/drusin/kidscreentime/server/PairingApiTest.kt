package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class PairingApiTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `Elterngerät koppelt mit dem Code aus dem Admin-Formular und bekommt ein 256-Bit-Token`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        val code = client.submitAdminPassword(TestServer.ADMIN_PASSWORD).also { assertEquals(HttpStatusCode.OK, it.status) }.adminCode()
        val response = client.redeem(code, "Handy von Mama")

        assertEquals(HttpStatusCode.OK, response.status)
        val paired = response.body<io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse>()
        assertEquals(DeviceRole.PARENT, paired.role)
        assertNull(paired.childId)
        assertEquals(32, Base64.getUrlDecoder().decode(paired.deviceToken).size)
    }

    @Test
    fun `Kopplungscode ist einmalig`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val code = server.services.pairing.createCode(DeviceRole.PARENT).code

        assertEquals(HttpStatusCode.OK, client.redeem(code).status)
        assertEquals(HttpStatusCode.BadRequest, client.redeem(code).status)
    }

    @Test
    fun `Kopplungscode verfällt nach 10 Minuten`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val stillValid = server.services.pairing.createCode(DeviceRole.PARENT).code
        val expired = server.services.pairing.createCode(DeviceRole.PARENT).code

        server.clock.advance(Duration.ofMinutes(10).minusSeconds(1))
        assertEquals(HttpStatusCode.OK, client.redeem(stillValid).status)

        server.clock.advance(Duration.ofSeconds(1))
        assertEquals(HttpStatusCode.BadRequest, client.redeem(expired).status)
    }

    @Test
    fun `Code wird auch mit Kleinbuchstaben und ohne Bindestrich akzeptiert`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val code = server.services.pairing.createCode(DeviceRole.PARENT).code

        assertEquals(HttpStatusCode.OK, client.redeem(code.replace("-", "").lowercase()).status)
    }

    @Test
    fun `Code und Token liegen am Server nur als Hash`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val code = server.services.pairing.createCode(DeviceRole.PARENT).code
        val paired = client.redeem(code).body<io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse>()

        val stored = DriverManager.getConnection("jdbc:sqlite:${server.config.databasePath}").use { c ->
            val codeHash = c.createStatement().executeQuery("SELECT code_hash FROM pairing_codes").use { it.next(); it.getString(1) }
            val tokenHash = c.createStatement().executeQuery("SELECT token_hash FROM devices").use { it.next(); it.getString(1) }
            codeHash to tokenHash
        }
        assertNotEquals(code, stored.first)
        assertNotEquals(paired.deviceToken, stored.second)
        assertFalse(stored.first.contains(code.replace("-", "")))
        assertEquals(64, stored.second.length)
    }

    @Test
    fun `Nach zehn falschen Codes antwortet das Einlösen mit 429, auch für einen gültigen Code`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val valid = server.services.pairing.createCode(DeviceRole.PARENT).code

        repeat(10) { assertEquals(HttpStatusCode.BadRequest, client.redeem("AAAA-AAAA").status) }

        assertEquals(HttpStatusCode.TooManyRequests, client.redeem(valid).status)
        server.clock.advance(Duration.ofMinutes(1))
        assertEquals(HttpStatusCode.OK, client.redeem(valid).status)
    }

    @Test
    fun `Rate-Limit gilt gemeinsam für Codes und Admin-Passwort`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        repeat(5) { client.redeem("AAAA-AAAA") }
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.submitAdminPassword("falsch").status) }

        assertEquals(HttpStatusCode.TooManyRequests, client.submitAdminPassword(TestServer.ADMIN_PASSWORD).status)
        assertEquals(HttpStatusCode.TooManyRequests, client.redeem("AAAA-AAAA").status)
    }

    @Test
    fun `Eltern-Snapshot liefert Serverzeit, Geräteliste und eine leere Kinderliste`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT, "Handy von Mama")
        val pairedAt = server.clock.now().toEpochMilli()

        server.clock.advance(Duration.ofMinutes(3))
        val response = client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.deviceToken}") }

        assertEquals(HttpStatusCode.OK, response.status)
        val snapshot = response.body<ParentSnapshot>()
        assertEquals(server.clock.now().toEpochMilli(), snapshot.serverTimeEpochMillis)
        assertTrue(snapshot.children.isEmpty())
        val device = snapshot.devices.single()
        assertEquals("Handy von Mama", device.name)
        assertEquals(DeviceRole.PARENT, device.role)
        assertEquals(pairedAt, device.pairedAtEpochMillis)
    }

    @Test
    fun `Zuletzt gesehen wird bei jedem authentifizierten Aufruf aktualisiert`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val parent = server.pairDevice(client, DeviceRole.PARENT)

        server.clock.advance(Duration.ofHours(1))
        val snapshot = client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${parent.deviceToken}") }
            .body<ParentSnapshot>()

        assertEquals(server.clock.now().toEpochMilli(), snapshot.devices.single().lastSeenEpochMillis)
    }

    @Test
    fun `Eltern-Snapshot ohne oder mit ungültigem Token antwortet mit 401`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        server.pairDevice(client, DeviceRole.PARENT)

        assertEquals(HttpStatusCode.Unauthorized, client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer falsch") }.status,
        )
    }

    @Test
    fun `Ein Kindgerät-Token bekommt am Eltern-Snapshot 403`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)

        val response = client.get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer ${child.deviceToken}") }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `Das Admin-Formular ist nur auf dem Admin-Port erreichbar und die API nicht dort`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        assertEquals(HttpStatusCode.OK, client.get(TestServer.ADMIN + ADMIN_PATH).status)
        assertEquals(HttpStatusCode.NotFound, client.get(TestServer.PUBLIC + ADMIN_PATH).status)
        assertEquals(HttpStatusCode.NotFound, client.submitAdminPassword(TestServer.ADMIN_PASSWORD, TestServer.PUBLIC).status)
        assertEquals(HttpStatusCode.NotFound, client.get(TestServer.ADMIN + ApiPaths.HEALTH).status)
    }

    @Test
    fun `Das Admin-Formular verlangt das Passwort und nennt bei Erfolg die Server-URL`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        val wrong = client.submitAdminPassword("falsch")
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertFalse(Regex("<code").containsMatchIn(wrong.bodyAsText()))

        val right = client.submitAdminPassword(TestServer.ADMIN_PASSWORD)
        assertEquals(HttpStatusCode.OK, right.status)
        assertTrue(right.bodyAsText().contains(server.config.publicServerUrl))
    }

    @Test
    fun `Nach einem Neustart auf derselben Datenbankdatei bleibt das Gerät gekoppelt`() {
        val first = TestServer(dir)
        val token = kotlinx.coroutines.runBlocking {
            var token = ""
            testApplication {
                first.install(this)
                token = first.pairDevice(first.client(this), DeviceRole.PARENT).deviceToken
            }
            token
        }

        testApplication {
            val second = TestServer(dir, first.clock).also { it.install(this) }
            val response = second.client(this)
                .get(TestServer.PUBLIC + ApiPaths.PARENT_SNAPSHOT) { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(Database.MIGRATIONS.size, second.services.database.schemaVersion())
        }
    }
}
