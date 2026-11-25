package io.github.drusin.kidscreentime.pairing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.net.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenStore = FakeTokenStore()
    private val requests = mutableListOf<HttpRequestData>()
    private var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson(PARENT_RESPONSE) }

    private var unpaired = 0

    private fun repository() = PairingRepository(
        createHttpClient(MockEngine { request -> requests += request; handler(request) }),
        tokenStore,
        PairingStorage(context),
        onUnpaired = { unpaired++ },
    )

    @Test
    fun `Erfolgreiche Kopplung macht das Gerät zum Elterngerät und legt das Token im Token-Speicher ab`() = runTest {
        val repository = repository()

        val result = repository.pair("kst.example.org/", " ABCD-EFGH ", " Handy von Mama ")

        assertEquals(PairResult.Success, result)
        val state = assertIs<DeviceState.Paired>(repository.state.value)
        assertEquals(DeviceRole.PARENT, state.device.role)
        assertEquals("https://kst.example.org", state.device.serverUrl)
        assertEquals("Handy von Mama", state.device.deviceName)
        assertEquals("tok-123", tokenStore.load())
        assertEquals("tok-123", repository.token())
    }

    @Test
    fun `Die Anfrage geht an den Einlöse-Pfad der Server-URL und trägt Code und Gerätename`() = runTest {
        repository().pair("https://kst.example.org", "ABCD-EFGH", "Handy")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://kst.example.org" + ApiPaths.PAIRING_REDEEM, request.url.toString())
        val body = String(request.body.toByteArray())
        assertEquals(true, body.contains("\"code\":\"ABCD-EFGH\"") && body.contains("\"deviceName\":\"Handy\""), body)
    }

    @Test
    fun `Nach einem App-Neustart ist das Gerät weiterhin gekoppelt`() = runTest {
        repository().pair("https://kst.example.org", "ABCD-EFGH", "Handy")

        val restarted = repository()

        val state = assertIs<DeviceState.Paired>(restarted.state.value)
        assertEquals(DeviceRole.PARENT, state.device.role)
        assertEquals("https://kst.example.org", state.device.serverUrl)
    }

    @Test
    fun `Ohne Token im Speicher beginnt die App wieder bei Gerät koppeln`() = runTest {
        repository().pair("https://kst.example.org", "ABCD-EFGH", "Handy")
        tokenStore.clear()

        assertEquals(DeviceState.Unpaired, repository().state.value)
    }

    @Test
    fun `Ungültiger Code lässt das Gerät ungekoppelt`() = runTest {
        handler = { respondJson("""{"message":"ungültig"}""", HttpStatusCode.BadRequest) }
        val repository = repository()

        assertEquals(PairResult.InvalidCode, repository.pair("https://kst.example.org", "XXXX-XXXX", "Handy"))
        assertEquals(DeviceState.Unpaired, repository.state.value)
        assertNull(tokenStore.load())
    }

    @Test
    fun `Rate-Limit des Servers wird als Fehler gemeldet`() = runTest {
        handler = { respondJson("""{"message":"zu viele"}""", HttpStatusCode.TooManyRequests) }

        assertEquals(PairResult.RateLimited, repository().pair("https://kst.example.org", "XXXX-XXXX", "Handy"))
    }

    @Test
    fun `Nicht erreichbarer Server wird als Fehler gemeldet`() = runTest {
        handler = { throw IOException("offline") }

        assertEquals(PairResult.ServerUnreachable, repository().pair("https://kst.example.org", "ABCD-EFGH", "Handy"))
    }

    @Test
    fun `Eine ungültige Server-URL wird ohne Netzzugriff abgelehnt`() = runTest {
        assertEquals(PairResult.InvalidServerUrl, repository().pair("ftp://x", "ABCD-EFGH", "Handy"))
        assertEquals(PairResult.InvalidServerUrl, repository().pair("   ", "ABCD-EFGH", "Handy"))
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `Entkoppeln vergisst Token und Daten`() = runTest {
        val repository = repository()
        repository.pair("https://kst.example.org", "ABCD-EFGH", "Handy")

        repository.unpair()

        assertEquals(DeviceState.Unpaired, repository.state.value)
        assertNull(tokenStore.load())
        assertEquals(DeviceState.Unpaired, repository().state.value)
    }

    @Test
    fun `Eine Entkopplung durch den Server vergisst Token und Daten und zeigt Gerät wurde entkoppelt, auch nach einem Neustart`() = runTest {
        val repository = repository()
        repository.pair("https://kst.example.org", "ABCD-EFGH", "Handy")

        repository.markRevoked()

        assertEquals(DeviceState.Revoked, repository.state.value)
        assertNull(tokenStore.load())
        assertEquals(1, unpaired, "Der Push-Verteiler erfährt davon")
        assertEquals(DeviceState.Revoked, repository().state.value, "Der Hinweis bleibt bis zum Bestätigen")

        repository.acknowledgeRevoked()

        assertEquals(DeviceState.Unpaired, repository.state.value)
        assertEquals(DeviceState.Unpaired, repository().state.value)
    }

    @Test
    fun `Ein ungekoppeltes Gerät wird nicht entkoppelt und ein neues Koppeln löscht den Hinweis`() = runTest {
        val repository = repository()
        repository.markRevoked()
        assertEquals(DeviceState.Unpaired, repository.state.value)
        assertEquals(0, unpaired)

        repository.pair("https://kst.example.org", "ABCD-EFGH", "Handy")
        repository.markRevoked()
        repository.acknowledgeRevoked()
        repository.pair("https://kst.example.org", "ABCD-EFGH", "Handy")
        tokenStore.clear()

        assertEquals(DeviceState.Unpaired, repository().state.value, "Kein alter Hinweis nach erneutem Koppeln")
    }

    private companion object {
        const val PARENT_RESPONSE = """{"deviceId":"d1","deviceToken":"tok-123","role":"PARENT"}"""

        fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }
}
