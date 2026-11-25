package io.github.drusin.kidscreentime.parent

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
import io.github.drusin.kidscreentime.api.DailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.RulesRequest
import io.github.drusin.kidscreentime.api.UndoDailyAdjustmentRequest
import io.github.drusin.kidscreentime.api.WeeklyTemplateRequest
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.CancellationException

sealed interface SnapshotResult {
    data class Loaded(val snapshot: ParentSnapshot) : SnapshotResult

    /** `401`: this device was entkoppelt or its token is invalid (ticket 12 shows "Gerät wurde entkoppelt"). */
    data object Unauthorized : SnapshotResult

    /** No network or no usable answer (ticket 17 adds the read-only cache for this case). */
    data object Unavailable : SnapshotResult
}

/**
 * The answer of every Eltern-Aktion (Tagesanpassung, later Wochenvorlage, Regeln, Entkoppeln ...): the server changes
 * its state and returns the new Eltern-Snapshot, which is the Abgleich of the Elterngerät.
 */
sealed interface ParentActionResult {
    data class Done(val snapshot: ParentSnapshot) : ParentActionResult

    /** `400`/`409`: the server refused the wish (unknown Kind, nothing to take back ...). The stand did not change. */
    data object Rejected : ParentActionResult

    data object Unauthorized : ParentActionResult

    /** No network or no usable answer. Eltern-Aktionen need the server (ticket 17 shows that state). */
    data object Unavailable : ParentActionResult
}

sealed interface CreateCodeResult {
    data class Created(val code: CreatePairingCodeResponse) : CreateCodeResult

    /** `400`: the server refused the wish (for example a second Kind). */
    data object Rejected : CreateCodeResult

    data object Unauthorized : CreateCodeResult

    data object Unavailable : CreateCodeResult
}

/** Calls of a Elterngerät. Every call carries the Geräte-Token as Bearer header. */
class ParentRepository(
    private val httpClient: HttpClient,
    private val pairing: PairingRepository,
) {
    suspend fun loadSnapshot(): SnapshotResult {
        val device = (pairing.state.value as? DeviceState.Paired)?.device ?: return SnapshotResult.Unauthorized
        val token = pairing.token() ?: return SnapshotResult.Unauthorized
        return try {
            val response = httpClient.get(device.serverUrl + ApiPaths.PARENT_SNAPSHOT) { bearerAuth(token) }
            when (response.status) {
                HttpStatusCode.OK -> SnapshotResult.Loaded(response.body())
                HttpStatusCode.Unauthorized -> SnapshotResult.Unauthorized
                else -> SnapshotResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SnapshotResult.Unavailable
        }
    }

    /** Creates a Kopplungscode for a Kindgerät or a further Elterngerät. */
    suspend fun createPairingCode(request: CreatePairingCodeRequest): CreateCodeResult {
        val device = (pairing.state.value as? DeviceState.Paired)?.device ?: return CreateCodeResult.Unauthorized
        val token = pairing.token() ?: return CreateCodeResult.Unauthorized
        return try {
            val response = httpClient.post(device.serverUrl + ApiPaths.PARENT_PAIRING_CODES) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK -> CreateCodeResult.Created(response.body())
                HttpStatusCode.BadRequest -> CreateCodeResult.Rejected
                HttpStatusCode.Unauthorized -> CreateCodeResult.Unauthorized
                else -> CreateCodeResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CreateCodeResult.Unavailable
        }
    }

    /** Books a Tagesanpassung of [deltaMinutes] for today for [childId]. */
    suspend fun bookAdjustment(childId: String, deltaMinutes: Int): ParentActionResult =
        action(ApiPaths.PARENT_DAILY_ADJUSTMENTS, DailyAdjustmentRequest(childId, deltaMinutes))

    /** Takes back the last Tagesanpassung of today (the server books the Gegenbuchung). */
    suspend fun undoLastAdjustment(childId: String): ParentActionResult =
        action(ApiPaths.PARENT_DAILY_ADJUSTMENTS_UNDO, UndoDailyAdjustmentRequest(childId))

    /** Sets the Wochenvorlage ([minutes] Mo–So), valid from tomorrow. */
    suspend fun setWeeklyTemplate(childId: String, minutes: List<Int>): ParentActionResult =
        action(ApiPaths.PARENT_WEEKLY_TEMPLATE, WeeklyTemplateRequest(childId, minutes))

    /** Sets name, Vorwarnschwelle and Überziehungsgrenze of the Kind. */
    suspend fun setRules(childId: String, name: String, warningThresholdMinutes: Int, overrunLimitMinutes: Int): ParentActionResult =
        action(ApiPaths.PARENT_RULES, RulesRequest(childId, name, warningThresholdMinutes, overrunLimitMinutes))

    /** The one way a Eltern-Aktion goes to the server: POST [body] to [path], expect the new [ParentSnapshot]. */
    private suspend inline fun <reified T : Any> action(path: String, body: T): ParentActionResult {
        val device = (pairing.state.value as? DeviceState.Paired)?.device ?: return ParentActionResult.Unauthorized
        val token = pairing.token() ?: return ParentActionResult.Unauthorized
        return try {
            val response = httpClient.post(device.serverUrl + path) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status) {
                HttpStatusCode.OK -> ParentActionResult.Done(response.body())
                HttpStatusCode.BadRequest, HttpStatusCode.Conflict -> ParentActionResult.Rejected
                HttpStatusCode.Unauthorized -> ParentActionResult.Unauthorized
                else -> ParentActionResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ParentActionResult.Unavailable
        }
    }
}
