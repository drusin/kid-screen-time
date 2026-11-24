package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.domain.clock.SystemMonotonicClock
import io.github.drusin.kidscreentime.domain.clock.SystemWallClock
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import kotlin.system.exitProcess

fun main() {
    val config = try {
        ServerConfig.fromEnvironment(System.getenv())
    } catch (e: ConfigurationException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    embeddedServer(CIO, configure = {
        connectors += EngineConnectorBuilder().apply { host = "0.0.0.0"; port = config.port }
        connectors += EngineConnectorBuilder().apply { host = "0.0.0.0"; port = config.adminPort }
    }) {
        serverModule(config, SystemWallClock, SystemMonotonicClock)
    }.start(wait = true)
}
