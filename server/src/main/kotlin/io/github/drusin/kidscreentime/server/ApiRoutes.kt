package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.ErrorResponse
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.RedeemPairingCodeRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
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
            RedeemResult.ChildHasDevice ->
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Das Kind hat schon ein Kindgerät. Das Ersetzen ist noch nicht möglich."))
            RedeemResult.InvalidCode -> {
                services.rateLimiter.recordFailure()
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Kopplungscode ungültig oder abgelaufen"))
            }
        }
    }

    authenticatedGet(ApiPaths.PARENT_SNAPSHOT, services, DeviceRole.PARENT) {
        call.respond(parentSnapshot(services))
    }

    authenticatedPost(ApiPaths.PARENT_PAIRING_CODES, services, DeviceRole.PARENT) {
        val request = call.receive<CreatePairingCodeRequest>()
        val childName = request.newChildName?.trim()
        val problem = when (request.role) {
            DeviceRole.PARENT ->
                if (request.childId != null || request.newChildName != null) "Ein Elterngerät gehört zu keinem Kind" else null
            DeviceRole.CHILD -> when {
                (request.childId != null) == (request.newChildName != null) -> "Ein Kindgerät braucht das Kind oder den Namen eines neuen Kindes"
                request.childId != null && !services.children.exists(request.childId!!) -> "Das Kind ist unbekannt"
                childName != null && childName.isEmpty() -> "Der Name des Kindes fehlt"
                childName != null && services.children.count() > 0 -> "Es gibt schon ein Kind"
                else -> null
            }
        }
        if (problem != null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(problem))
            return@authenticatedPost
        }
        val created = services.pairing.createCode(request.role, request.childId, childName)
        val childId = request.childId
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(
            CreatePairingCodeResponse(
                code = created.code,
                expiresAtEpochMillis = created.expiresAtEpochMillis,
                serverTimeEpochMillis = services.wallClock.now().toEpochMilli(),
                serverUrl = services.config.publicServerUrl,
                role = request.role,
                childId = childId,
                childName = childName ?: childId?.let { services.children.parentChildren().firstOrNull { c -> c.childId == it }?.name },
                replacesDeviceName = if (request.role == DeviceRole.CHILD && childId != null) services.children.activeChildDeviceName(childId) else null,
                snapshot = parentSnapshot(services),
            ),
        )
    }

    // The sync of the Kindgerät: upserts the Sitzungen of its Outbox, answers with the complete Kind-Snapshot.
    authenticatedPost(ApiPaths.CHILD_SYNC, services, DeviceRole.CHILD) { device ->
        val childId = device.childId
        if (childId == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@authenticatedPost
        }
        val request = call.receive<ChildSyncRequest>()
        when (val result = services.children.sync(childId, request.sessions)) {
            is SyncResult.Synced -> call.respond(result.snapshot)
            is SyncResult.Invalid -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.reason))
            SyncResult.ForeignSession -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("Die Sitzung gehört zu einem anderen Kind"))
        }
    }
}

private fun parentSnapshot(services: ServerServices) = ParentSnapshot(
    serverTimeEpochMillis = services.wallClock.now().toEpochMilli(),
    timeZone = services.config.familyZone.id,
    devices = services.devices.listActive(),
    children = services.children.parentChildren(),
)
