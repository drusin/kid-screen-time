package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.HealthResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class HealthTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `Health-Endpunkt antwortet mit ok und der Serverzeit der injizierten Uhr`() = testApplication {
        val server = TestServer(dir)
        server.install(this)
        val client = server.client(this)

        server.clock.advance(Duration.ofHours(2))
        val response = client.get(TestServer.PUBLIC + ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthResponse>()
        assertEquals("ok", body.status)
        assertEquals(server.clock.now().toEpochMilli(), body.serverTimeEpochMillis)
    }
}
