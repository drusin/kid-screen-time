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
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
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
    val push: PushService,
) {
    companion object {
        fun create(
            config: ServerConfig,
            wallClock: WallClock,
            monotonicClock: MonotonicClock,
            publisher: PushPublisher = HttpPushPublisher(),
        ): ServerServices {
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
                push = PushService(database, config, publisher),
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
    monitor.subscribe(ApplicationStopped) {
        services.push.close()
        services.database.close()
    }
    routing {
        localPort(config.port) {
            get(ApiPaths.HEALTH) {
                call.respond(HealthResponse(status = "ok", serverTimeEpochMillis = wallClock.now().toEpochMilli()))
            }
            apiRoutes(services)
        }
        localPort(config.adminPort) {
            adminRoutes(services)
        }
    }
}

/**
 * Routes below this one match only connections that arrived on the local [port]. Ktor's own `port(...)` compares
 * the port of the Host header instead, which is wrong behind a reverse proxy (no port in Host) and lets a client
 * pick the route set by sending `Host: x:<adminPort>`. The local port cannot be influenced by the request.
 */
fun Route.localPort(port: Int, build: Route.() -> Unit): Route =
    createChild(LocalPortRouteSelector(port)).apply(build)

private class LocalPortRouteSelector(private val port: Int) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        if (context.call.request.local.localPort == port) {
            RouteSelectorEvaluation.Constant
        } else {
            RouteSelectorEvaluation.Failed
        }

    override fun toString(): String = "(localPort = $port)"
}
