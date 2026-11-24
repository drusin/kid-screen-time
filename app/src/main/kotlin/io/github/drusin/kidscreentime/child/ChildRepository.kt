package io.github.drusin.kidscreentime.child

import androidx.room.withTransaction
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.db.AppDatabase
import io.github.drusin.kidscreentime.db.ChildSnapshotEntity
import io.github.drusin.kidscreentime.db.OutboxSessionEntity
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.CancellationException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** The last Kind-Snapshot from Room and when this device received it (device wall time). */
data class StoredChildSnapshot(val snapshot: ChildSnapshot, val receivedAtEpochMillis: Long)

/** A Sitzung of the Outbox: started or stopped at this device, not yet confirmed by the server. */
data class PendingSession(val id: String, val startWallMillis: Long, val endWallMillis: Long? = null)

/** Everything the screen calculates with: the last snapshot plus what only this device knows so far (the Outbox). */
data class ChildLocalState(val stored: StoredChildSnapshot, val pending: List<PendingSession>)

sealed interface ChildSyncResult {
    data object Synced : ChildSyncResult

    /** `401`: this device was entkoppelt or its token is invalid (ticket 12 shows "Gerät wurde entkoppelt"). The Outbox stays. */
    data object Unauthorized : ChildSyncResult

    /** No network or no usable answer. The device keeps calculating with the stored snapshot and its Outbox. */
    data object Unavailable : ChildSyncResult
}

/**
 * Calls and state of a Kindgerät. The Outbox is the only way a change leaves the device (Sync-Protokoll): [startSession]
 * and [stopSession] write to Room first, the screen shows the result from [local] at once, and [sync] delivers the
 * Outbox and stores the returned snapshot. There is no separate online path.
 */
class ChildRepository(
    private val httpClient: HttpClient,
    private val pairing: PairingRepository,
    private val database: AppDatabase,
    private val wallClock: WallClock,
    private val monotonicClock: MonotonicClock,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotDao = database.childSnapshotDao()
    private val outboxDao = database.outboxDao()
    private val syncLock = Mutex()

    /** The stored snapshot, null until the first sync succeeded. */
    val stored: Flow<StoredChildSnapshot?> = snapshotDao.observe().map { entity ->
        entity?.let { runCatching { StoredChildSnapshot(json.decodeFromString<ChildSnapshot>(it.json), it.receivedAtEpochMillis) }.getOrNull() }
    }

    /** The snapshot with the Outbox on top, null until the first sync succeeded. Changes as soon as the Outbox does. */
    val local: Flow<ChildLocalState?> = combine(stored, outboxDao.observeAll()) { stored, outbox ->
        stored?.let { ChildLocalState(it, outbox.map { row -> PendingSession(row.sessionId, row.startWallMillis, row.endWallMillis) }) }
    }

    /** Starts a Sitzung: new UUID, written to the Outbox. The caller checks the rules (Restzeit > 0, none running) and then syncs. */
    suspend fun startSession(): String {
        val id = UUID.randomUUID().toString()
        outboxDao.upsert(
            OutboxSessionEntity(
                sessionId = id,
                startMonotonicMillis = monotonicClock.elapsed().toMillis(),
                startWallMillis = wallClock.now().toEpochMilli(),
            ),
        )
        return id
    }

    /**
     * Stops the Sitzung [sessionId]: its row in the Outbox gets the end. The caller syncs afterwards. [at] is the end
     * when it lies in the past (the Automatischer Stopp ends at the time the Überziehungsgrenze was reached, even if the
     * alarm fired late); default is now.
     */
    suspend fun stopSession(sessionId: String, at: Instant? = null) {
        database.withTransaction {
            val row = outboxDao.find(sessionId) ?: run {
                // Known only from the snapshot (started at an earlier time): the Stopp brings its Start with it.
                val start = snapshotSession(sessionId)?.startEpochMillis ?: return@withTransaction
                OutboxSessionEntity(sessionId, monotonicStartFor(start), start)
            }
            if (row.endWallMillis != null) return@withTransaction
            val now = wallClock.now()
            val end = at?.takeIf { it.isBefore(now) } ?: now
            outboxDao.upsert(
                row.copy(
                    // Same age as at the wall clock: the monotonic end is as far in the past as the end is.
                    endMonotonicMillis = monotonicClock.elapsed().toMillis() - Duration.between(end, now).toMillis(),
                    endWallMillis = end.toEpochMilli(),
                    revision = row.revision + 1,
                ),
            )
        }
    }

    /** A start time from the snapshot, translated to the monotonic clock via how long ago it was. */
    private fun monotonicStartFor(startWallMillis: Long): Long =
        monotonicClock.elapsed().toMillis() - (wallClock.now().toEpochMilli() - startWallMillis).coerceAtLeast(0)

    private suspend fun snapshotSession(sessionId: String) = snapshotDao.current()
        ?.let { runCatching { json.decodeFromString<ChildSnapshot>(it.json) }.getOrNull() }
        ?.todaySessions?.firstOrNull { it.id == sessionId }

    /**
     * Sends the Outbox (all open Sitzungen in start order, times as age by the monotonic clock) and stores the
     * snapshot of the answer. Afterwards the delivered rows are removed in the same transaction, unless a row changed
     * during the call. Only one sync runs at a time.
     */
    suspend fun sync(): ChildSyncResult = syncLock.withLock {
        val device = (pairing.state.value as? DeviceState.Paired)?.device ?: return ChildSyncResult.Unauthorized
        val token = pairing.token() ?: return ChildSyncResult.Unauthorized
        val sent = outboxDao.all()
        val nowMonotonic = monotonicClock.elapsed().toMillis()
        val request = ChildSyncRequest(sent.map { it.toUpdate(nowMonotonic) })
        try {
            val response = httpClient.post(device.serverUrl + ApiPaths.CHILD_SYNC) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val snapshot = response.body<ChildSnapshot>()
                    val entity = ChildSnapshotEntity(
                        json = json.encodeToString(ChildSnapshot.serializer(), snapshot),
                        receivedAtEpochMillis = wallClock.now().toEpochMilli(),
                    )
                    database.withTransaction {
                        snapshotDao.save(entity)
                        sent.forEach { outboxDao.deleteDelivered(it.sessionId, it.revision) }
                    }
                    ChildSyncResult.Synced
                }
                HttpStatusCode.Unauthorized -> ChildSyncResult.Unauthorized
                else -> ChildSyncResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ChildSyncResult.Unavailable
        }
    }
}

private fun OutboxSessionEntity.toUpdate(nowMonotonicMillis: Long) = SessionUpdate(
    id = sessionId,
    startAgeMillis = (nowMonotonicMillis - startMonotonicMillis).coerceAtLeast(0),
    endAgeMillis = endMonotonicMillis?.let { (nowMonotonicMillis - it).coerceAtLeast(0) },
)
