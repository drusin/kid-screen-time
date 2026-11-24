package io.github.drusin.kidscreentime.parent

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
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
}
