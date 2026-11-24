package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.RedeemPairingCodeRequest
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.domain.clock.ManualClock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.testing.ApplicationTestBuilder
import java.nio.file.Path
import java.time.ZoneId

/**
 * Test setup for the server HTTP API (a test fixture of `server`, also used by the app's sync scenario tests): a real SQLite file in a temp folder, one [ManualClock] for both clocks.
 * Reuse it in every server test:
 *
 * ```
 * @TempDir lateinit var dir: Path
 * @Test fun `...`() = testApplication {
 *     val server = TestServer(dir); server.install(this)
 *     val client = server.client(this)
 * }
 * ```
 * The public API is on [PUBLIC], the Admin-Formular on [ADMIN]. Build a second [TestServer] on the same
 * folder to simulate a restart (same database file). The timed events do not run on their own here: after
 * moving the clock call `services.scheduler.runDue()` (what the loop of the real server does when a time is due).
 */
class TestServer(
    dir: Path,
    val clock: ManualClock = ManualClock(),
    /** Where the server publishes pushes; tests point it at a fake HTTP endpoint instead of ntfy. */
    ntfyInternalUrl: String = "http://ntfy:80",
    publicPort: Int = PUBLIC_PORT,
    adminPort: Int = ADMIN_PORT,
) {
    val config = ServerConfig(
        adminPassword = ADMIN_PASSWORD,
        familyZone = ZoneId.of("Europe/Berlin"),
        publicServerUrl = "https://kst.example.org",
        ntfyPublicUrl = "https://ntfy.example.org",
        ntfyInternalUrl = ntfyInternalUrl,
        databasePath = dir.resolve("kid-screen-time.db"),
        port = publicPort,
        adminPort = adminPort,
    )
    val services: ServerServices = ServerServices.create(config, clock, clock)

    fun install(builder: ApplicationTestBuilder) {
        builder.engine {
            connectors += EngineConnectorBuilder().apply { host = "localhost"; port = config.port }
            connectors += EngineConnectorBuilder().apply { host = "localhost"; port = config.adminPort }
        }
        builder.application { serverModule(config, clock, clock, services, runScheduler = false) }
    }

    fun client(builder: ApplicationTestBuilder): HttpClient =
        builder.createClient { install(ContentNegotiation) { json(ApiJson) } }

    companion object {
        const val ADMIN_PASSWORD = "geheim-im-heimnetz"
        const val PUBLIC_PORT = 8080
        const val ADMIN_PORT = 8081
        const val PUBLIC = "http://localhost:$PUBLIC_PORT"
        const val ADMIN = "http://localhost:$ADMIN_PORT"
    }
}

/** Creates a Kopplungscode of [role] directly in the service and redeems it over HTTP. */
suspend fun TestServer.pairDevice(
    client: HttpClient,
    role: DeviceRole,
    name: String = "Gerät",
): RedeemPairingCodeResponse {
    val code = when (role) {
        DeviceRole.PARENT -> services.pairing.createCode(DeviceRole.PARENT)
        DeviceRole.CHILD -> services.pairing.createCode(DeviceRole.CHILD, newChildName = "Mia")
    }.code
    val response = client.redeem(code, name)
    check(response.status == HttpStatusCode.OK) { "Kopplung fehlgeschlagen: ${response.status}" }
    return response.body()
}

suspend fun HttpClient.redeem(code: String, deviceName: String = "Handy"): HttpResponse =
    post(TestServer.PUBLIC + ApiPaths.PAIRING_REDEEM) {
        contentType(ContentType.Application.Json)
        setBody(RedeemPairingCodeRequest(code = code, deviceName = deviceName))
    }


suspend fun HttpClient.submitAdminPassword(password: String, baseUrl: String = TestServer.ADMIN): HttpResponse =
    submitForm(baseUrl + ADMIN_PARENT_CODE_PATH, Parameters.build { append("password", password) })

/** Reads the Kopplungscode out of the Admin-Formular answer. */
suspend fun HttpResponse.adminCode(): String =
    Regex("<code[^>]*>([A-Z2-9-]+)</code>").find(bodyAsText())?.groupValues?.get(1) ?: error("Kein Kopplungscode in der Antwort")
