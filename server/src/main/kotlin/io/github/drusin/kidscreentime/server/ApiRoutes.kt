package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.ErrorResponse
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
import io.github.drusin.kidscreentime.api.DailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.api.RulesRequest
import io.github.drusin.kidscreentime.api.UndoDailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.WeeklyTemplateRequest
import io.github.drusin.kidscreentime.api.PushRegistration
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
import kotlin.math.abs

/**
 * Defines a route that needs a Geräte-Token of [role]. Pattern for every authenticated endpoint:
 * `401` without or with an invalid or entkoppelt token, `403` for the wrong role, otherwise the handler runs.
 */
fun Route.authenticatedGet(path: String, services: ServerServices, role: DeviceRole, handler: suspend RoutingContext.(Device) -> Unit) {
    get(path) { withDevice(services, setOf(role), handler) }
}

fun Route.authenticatedPost(path: String, services: ServerServices, role: DeviceRole, handler: suspend RoutingContext.(Device) -> Unit) {
    post(path) { withDevice(services, setOf(role), handler) }
}

/** For the few endpoints that both roles use (push registration): `403` only when the role is in neither of [roles]. */
fun Route.authenticatedPost(path: String, services: ServerServices, roles: Set<DeviceRole>, handler: suspend RoutingContext.(Device) -> Unit) {
    post(path) { withDevice(services, roles, handler) }
}

private suspend fun RoutingContext.withDevice(
    services: ServerServices,
    roles: Set<DeviceRole>,
    handler: suspend RoutingContext.(Device) -> Unit,
) {
    val token = call.request.header("Authorization")
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring("Bearer ".length)?.trim()
    val device = token?.takeIf { it.isNotEmpty() }?.let(services.devices::authenticate)
    when {
        device == null -> call.respond(HttpStatusCode.Unauthorized)
        device.role !in roles -> call.respond(HttpStatusCode.Forbidden)
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
        request.push?.let {
            try {
                services.push.validate(it)
            } catch (e: InvalidPushRegistrationException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Push-Registrierung ungültig"))
                return@post
            }
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

    // Eltern-Aktion (Pattern: Aktion, Stand ändern, notifyStandChanged, Resync-Signal ans Kind, neuer Eltern-Snapshot als Antwort).
    authenticatedPost(ApiPaths.PARENT_DAILY_ADJUSTMENTS, services, DeviceRole.PARENT) { device ->
        val request = call.receive<DailyAdjustmentRequest>()
        val problem = when {
            request.deltaMinutes == 0 -> "Die Tagesanpassung darf nicht 0 sein"
            abs(request.deltaMinutes) > MAX_ADJUSTMENT_MINUTES -> "Die Tagesanpassung ist zu groß"
            else -> null
        }
        if (problem != null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(problem))
            return@authenticatedPost
        }
        when (services.children.bookAdjustment(request.childId, request.deltaMinutes, device.id)) {
            ChildService.AdjustmentResult.Booked -> {
                call.respond(parentSnapshot(services))
                services.push.signalChild(request.childId)
            }
            else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Das Kind ist unbekannt"))
        }
    }

    authenticatedPost(ApiPaths.PARENT_DAILY_ADJUSTMENTS_UNDO, services, DeviceRole.PARENT) { device ->
        val request = call.receive<UndoDailyAdjustmentRequest>()
        when (services.children.undoLastAdjustment(request.childId, device.id)) {
            ChildService.AdjustmentResult.UnknownChild -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Das Kind ist unbekannt"))
            ChildService.AdjustmentResult.NothingToTakeBack ->
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Es gibt keine Tagesanpassung zum Zurücknehmen"))
            ChildService.AdjustmentResult.Booked -> {
                call.respond(parentSnapshot(services))
                services.push.signalChild(request.childId)
            }
        }
    }

    authenticatedPost(ApiPaths.PARENT_WEEKLY_TEMPLATE, services, DeviceRole.PARENT) {
        val request = call.receive<WeeklyTemplateRequest>()
        val problem = when {
            request.minutes.size != 7 -> "Die Wochenvorlage braucht sieben Werte (Mo–So)"
            request.minutes.any { it < 0 || it > MAX_ADJUSTMENT_MINUTES } -> "Ein Tageskontingent liegt außerhalb von 0 bis 24 Stunden"
            else -> null
        }
        if (problem != null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(problem))
            return@authenticatedPost
        }
        when (services.children.setWeeklyTemplate(request.childId, request.minutes)) {
            ChildService.ChangeResult.Changed -> {
                call.respond(parentSnapshot(services))
                services.push.signalChild(request.childId)
            }
            ChildService.ChangeResult.UnknownChild -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Das Kind ist unbekannt"))
        }
    }

    authenticatedPost(ApiPaths.PARENT_RULES, services, DeviceRole.PARENT) {
        val request = call.receive<RulesRequest>()
        val name = request.name.trim()
        val problem = when {
            name.isEmpty() -> "Der Name des Kindes fehlt"
            request.warningThresholdMinutes !in 0..MAX_ADJUSTMENT_MINUTES -> "Die Vorwarnschwelle ist ungültig"
            request.overrunLimitMinutes !in 0..MAX_ADJUSTMENT_MINUTES -> "Die Überziehungsgrenze ist ungültig"
            else -> null
        }
        if (problem != null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(problem))
            return@authenticatedPost
        }
        when (services.children.setRules(request.childId, name, request.warningThresholdMinutes, request.overrunLimitMinutes)) {
            ChildService.ChangeResult.Changed -> {
                call.respond(parentSnapshot(services))
                services.push.signalChild(request.childId)
            }
            ChildService.ChangeResult.UnknownChild -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Das Kind ist unbekannt"))
        }
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

    // Both roles keep their UnifiedPush endpoint and WebPush keys up to date here (they change with the distributor).
    authenticatedPost(ApiPaths.PUSH_REGISTRATION, services, setOf(DeviceRole.PARENT, DeviceRole.CHILD)) { device ->
        val registration = call.receive<PushRegistration>()
        try {
            services.push.validate(registration)
        } catch (e: InvalidPushRegistrationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Push-Registrierung ungültig"))
            return@authenticatedPost
        }
        services.push.register(device.id, registration)
        call.respond(HttpStatusCode.NoContent)
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
            is SyncResult.Synced -> {
                call.respond(result.snapshot)
                // After the answer: publishing never delays the Kindgerät. Elterngeräte learn about a new Start or Stopp.
                if (result.changed) services.push.signalParents()
            }
            is SyncResult.Invalid -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.reason))
            SyncResult.ForeignSession -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("Die Sitzung gehört zu einem anderen Kind"))
        }
    }
}

/** A single Tagesanpassung is at most one day (a typo guard, not a rule of `domain`). */
private const val MAX_ADJUSTMENT_MINUTES = 24 * 60

private fun parentSnapshot(services: ServerServices) = ParentSnapshot(
    serverTimeEpochMillis = services.wallClock.now().toEpochMilli(),
    timeZone = services.config.familyZone.id,
    devices = services.devices.listActive(),
    children = services.children.parentChildren(),
)
