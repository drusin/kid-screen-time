package io.github.drusin.kidscreentime.child

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.db.ChildSnapshotDao
import io.github.drusin.kidscreentime.db.ChildSnapshotEntity
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** The last Kind-Snapshot from Room and when this device received it (device wall time). */
data class StoredChildSnapshot(val snapshot: ChildSnapshot, val receivedAtEpochMillis: Long)

sealed interface ChildRefreshResult {
    data object Refreshed : ChildRefreshResult

    /** `401`: this device was entkoppelt or its token is invalid (ticket 12 shows "Gerät wurde entkoppelt"). */
    data object Unauthorized : ChildRefreshResult

    /** No network or no usable answer. The device keeps calculating with the stored snapshot. */
    data object Unavailable : ChildRefreshResult
}

/**
 * Calls of a Kindgerät. The snapshot goes into Room and the screen reads it from there, so it works offline.
 * Ticket 05 replaces the plain GET with the sync that sends the Outbox and returns the same snapshot.
 */
class ChildRepository(
    private val httpClient: HttpClient,
    private val pairing: PairingRepository,
    private val dao: ChildSnapshotDao,
    private val wallClock: WallClock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The stored snapshot, null until the first sync succeeded. */
    val stored: Flow<StoredChildSnapshot?> = dao.observe().map { entity ->
        entity?.let { runCatching { StoredChildSnapshot(json.decodeFromString<ChildSnapshot>(it.json), it.receivedAtEpochMillis) }.getOrNull() }
    }

    suspend fun refresh(): ChildRefreshResult {
        val device = (pairing.state.value as? DeviceState.Paired)?.device ?: return ChildRefreshResult.Unauthorized
        val token = pairing.token() ?: return ChildRefreshResult.Unauthorized
        return try {
            val response = httpClient.get(device.serverUrl + ApiPaths.CHILD_SNAPSHOT) { bearerAuth(token) }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val snapshot = response.body<ChildSnapshot>()
                    dao.save(ChildSnapshotEntity(json = json.encodeToString(ChildSnapshot.serializer(), snapshot), receivedAtEpochMillis = wallClock.now().toEpochMilli()))
                    ChildRefreshResult.Refreshed
                }
                HttpStatusCode.Unauthorized -> ChildRefreshResult.Unauthorized
                else -> ChildRefreshResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ChildRefreshResult.Unavailable
        }
    }
}
