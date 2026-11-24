package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.HealthResponse
import io.github.drusin.kidscreentime.domain.clock.ManualClock
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun `Health-Endpunkt antwortet mit ok und der Serverzeit der injizierten Uhr`() = testApplication {
        val clock = ManualClock()
        application { serverModule(clock, clock) }
        val client = createClient { install(ContentNegotiation) { json() } }

        clock.advance(Duration.ofHours(2))
        val response = client.get(ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthResponse>()
        assertEquals("ok", body.status)
        assertEquals(clock.now().toEpochMilli(), body.serverTimeEpochMillis)
    }
}
