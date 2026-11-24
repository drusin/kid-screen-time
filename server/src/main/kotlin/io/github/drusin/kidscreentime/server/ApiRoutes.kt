package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ErrorResponse
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.RedeemPairingCodeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Defines a route that needs a Geräte-Token of [role]. Pattern for every authenticated endpoint:
 * `401` without or with an invalid or entkoppelt token, `403` for the wrong role, otherwise the handler runs.
 */
fun Route.authenticatedGet(path: String, services: ServerServices, role: DeviceRole, handler: suspend RoutingContext.(Device) -> Unit) {
    get(path) { withDevice(services, role, handler) }
}

fun Route.authenticatedPost(path: String, services: ServerServices, role: DeviceRole, handler: suspend RoutingContext.(Device) -> Unit) {
    post(path) { withDevice(services, role, handler) }
}

private suspend fun RoutingContext.withDevice(
    services: ServerServices,
    role: DeviceRole,
    handler: suspend RoutingContext.(Device) -> Unit,
) {
    val token = call.request.header("Authorization")
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring("Bearer ".length)?.trim()
    val device = token?.takeIf { it.isNotEmpty() }?.let(services.devices::authenticate)
    when {
        device == null -> call.respond(HttpStatusCode.Unauthorized)
        device.role != role -> call.respond(HttpStatusCode.Forbidden)
        else -> handler(device)
    }
}

fun Route.apiRoutes(services: ServerServices) {
    post(ApiPaths.PAIRING_REDEEM) {
        if (services.rateLimiter.isBlocked()) {
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Zu viele Fehlversuche, bitte später erneut versuchen"))
            return@post
        }
        val request = call.receive<RedeemPairingCodeRequest>()
        if (request.deviceName.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Gerätename fehlt"))
            return@post
        }
        when (val result = services.pairing.redeem(request.code, request.deviceName.trim(), request.push)) {
            is RedeemResult.Paired -> call.respond(result.response)
            RedeemResult.InvalidCode -> {
                services.rateLimiter.recordFailure()
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Kopplungscode ungültig oder abgelaufen"))
            }
        }
    }

    authenticatedGet(ApiPaths.PARENT_SNAPSHOT, services, DeviceRole.PARENT) {
        call.respond(parentSnapshot(services))
    }
}

private fun parentSnapshot(services: ServerServices) = ParentSnapshot(
    serverTimeEpochMillis = services.wallClock.now().toEpochMilli(),
    devices = services.devices.listActive(),
    children = emptyList<ParentChildSnapshot>(),
)
