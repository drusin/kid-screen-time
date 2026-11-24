package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.HealthResponse
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.port
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Everything the routes need. Tests build it themselves to seed data or to restart on the same database file. */
class ServerServices(
    val config: ServerConfig,
    val wallClock: WallClock,
    val database: Database,
    val children: ChildService,
    val pairing: PairingService,
    val devices: DeviceService,
    val automaticStops: AutomaticStopEvents,
    val scheduler: EventScheduler,
    val rateLimiter: FailureRateLimiter,
) {
    companion object {
        fun create(config: ServerConfig, wallClock: WallClock, monotonicClock: MonotonicClock): ServerServices {
            val database = Database.open(config.databasePath)
            val children = ChildService(database, wallClock, config.familyZone)
            val automaticStops = AutomaticStopEvents(children)
            // More sources (midnight, ticket 11; notification "Restzeit 0", ticket 14) are added to this list.
            val scheduler = EventScheduler(wallClock, children, listOf(automaticStops))
            children.onStandChanged(scheduler::wake)
            return ServerServices(
                config = config,
                wallClock = wallClock,
                database = database,
                children = children,
                pairing = PairingService(database, wallClock, children),
                devices = DeviceService(database, wallClock),
                automaticStops = automaticStops,
                scheduler = scheduler,
                rateLimiter = FailureRateLimiter(monotonicClock),
            )
        }
    }
}

/** The JSON settings of the REST contract. Unknown fields are ignored (compatibility with newer clients). */
val ApiJson = Json { ignoreUnknownKeys = true }

/**
 * The Ktor module. Both clocks are injected so tests can control time. Nothing in the server may read
 * the system clock directly (architecture test).
 *
 * The Event scheduler (timed events such as the Automatischer Stopp) runs as a coroutine of the application.
 *
 * The public API answers on [ServerConfig.port], the Admin-Formular only on [ServerConfig.adminPort]. The two
 * route sets are separated by the local port of the connection, so neither is reachable on the other port.
 */
fun Application.serverModule(
    config: ServerConfig,
    wallClock: WallClock,
    monotonicClock: MonotonicClock,
    services: ServerServices = ServerServices.create(config, wallClock, monotonicClock),
    runScheduler: Boolean = true,
) {
    install(ContentNegotiation) { json(ApiJson) }
    // The scheduler loop ends with the application. Tests pass false and call `services.scheduler.runDue()` after moving the clock.
    val schedulerJob = if (runScheduler) launch(Dispatchers.IO) { services.scheduler.run() } else null
    monitor.subscribe(ApplicationStopping) { schedulerJob?.cancel() }
    monitor.subscribe(ApplicationStopped) { services.database.close() }
    routing {
        port(config.port) {
            get(ApiPaths.HEALTH) {
                call.respond(HealthResponse(status = "ok", serverTimeEpochMillis = wallClock.now().toEpochMilli()))
            }
            apiRoutes(services)
        }
        port(config.adminPort) {
            adminRoutes(services)
        }
    }
}
