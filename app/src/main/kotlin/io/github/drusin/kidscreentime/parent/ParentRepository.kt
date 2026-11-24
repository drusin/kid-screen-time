package io.github.drusin.kidscreentime.parent

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.CancellationException

sealed interface SnapshotResult {
    data class Loaded(val snapshot: ParentSnapshot) : SnapshotResult

    /** `401`: this device was entkoppelt or its token is invalid (ticket 12 shows "Gerät wurde entkoppelt"). */
    data object Unauthorized : SnapshotResult

    /** No network or no usable answer (ticket 17 adds the read-only cache for this case). */
    data object Unavailable : SnapshotResult
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
}
