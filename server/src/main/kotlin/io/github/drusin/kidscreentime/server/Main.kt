package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.domain.clock.SystemMonotonicClock
import io.github.drusin.kidscreentime.domain.clock.SystemWallClock
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        serverModule(SystemWallClock, SystemMonotonicClock)
    }.start(wait = true)
}
