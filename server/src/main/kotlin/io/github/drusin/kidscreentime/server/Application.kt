package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.HealthResponse
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * The Ktor module. Both clocks are injected so tests can control time. Nothing in the server may read
 * the system clock directly (architecture test).
 */
fun Application.serverModule(wallClock: WallClock, monotonicClock: MonotonicClock) {
    install(ContentNegotiation) { json() }
    routing {
        get(ApiPaths.HEALTH) {
            call.respond(HealthResponse(status = "ok", serverTimeEpochMillis = wallClock.now().toEpochMilli()))
        }
    }
}
