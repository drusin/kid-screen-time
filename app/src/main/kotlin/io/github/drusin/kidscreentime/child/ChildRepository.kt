package io.github.drusin.kidscreentime.child

import androidx.room.withTransaction
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.db.AppDatabase
import io.github.drusin.kidscreentime.db.ChildSnapshotEntity
import io.github.drusin.kidscreentime.db.OutboxSessionEntity
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.github.drusin.kidscreentime.sync.SyncRetry
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
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /**
     * `401`: this device was entkoppelt (or replaced by a new Kindgerät) or its token is invalid. The Outbox and the stored
     * snapshot are discarded, the screen shows "Gerät wurde entkoppelt" and the Alarmkern cancels its alarms.
     */
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
    /** Repeats a failed sync when the network is back (WorkManager). */
    private val syncRetry: SyncRetry = SyncRetry {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotDao = database.childSnapshotDao()
    private val outboxDao = database.outboxDao()
    private val syncLock = Mutex()

    private fun decode(entity: ChildSnapshotEntity): StoredChildSnapshot? =
        runCatching { StoredChildSnapshot(json.decodeFromString<ChildSnapshot>(entity.json), entity.receivedAtEpochMillis) }.getOrNull()

    /** The stored snapshot, null until the first sync succeeded. */
    val stored: Flow<StoredChildSnapshot?> = snapshotDao.observe().map { it?.let(::decode) }

    /**
     * The snapshot with the Outbox on top, null until the first sync succeeded. Changes as soon as the Outbox does.
     * Snapshot and Outbox are read together in one transaction after every change of either table. Combining two separate
     * Room flows would let a sync, which writes both in one transaction, show a mixed state for a moment (new Outbox, old
     * snapshot: "the Sitzung is gone"), and the Alarmkern would plan from it, cancel the alarm and forget its Alarmverlauf.
     */
    val local: Flow<ChildLocalState?> = database.invalidationTracker.createFlow("child_snapshot", "outbox_session")
        .map {
            database.withTransaction {
                val stored = snapshotDao.current()?.let(::decode)
                val outbox = outboxDao.all()
                stored?.let { ChildLocalState(it, outbox.map { row -> PendingSession(row.sessionId, row.startWallMillis, row.endWallMillis) }) }
            }
        }
        .distinctUntilChanged()

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
     * alarm fired late); default is now. [reason] is sent to the server with the end (Verlauf: "Automatisch gestoppt").
     */
    suspend fun stopSession(sessionId: String, at: Instant? = null, reason: SessionEndReason = SessionEndReason.STOPPED) {
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
                    endReason = reason.name,
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
        val clocks = SyncClocks(monotonicClock.elapsed().toMillis(), wallClock.now().toEpochMilli())
        val request = ChildSyncRequest(sent.map { it.toUpdate(clocks) })
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
                HttpStatusCode.Unauthorized -> revoked()
                else -> unavailable()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            unavailable()
        }
    }

    /**
     * The server entkoppelt this device. What it still has in the Outbox must not reach the server, and the snapshot is
     * no longer its stand: both are deleted in one transaction. That makes [local] empty, which the Alarmkern answers
     * with `replan(null)` (alarms cancelled, `AlarmMemory` cleared, a ringing Alarm ends). The new Kindgerät takes over
     * a running Sitzung through its UUID in the snapshot.
     */
    private suspend fun revoked(): ChildSyncResult {
        database.withTransaction {
            outboxDao.clear()
            snapshotDao.clear()
        }
        pairing.markRevoked()
        return ChildSyncResult.Unauthorized
    }

    /** No usable answer: the Outbox stays and the WorkManager job tries again as soon as there is a network. */
    private fun unavailable(): ChildSyncResult {
        syncRetry.schedule()
        return ChildSyncResult.Unavailable
    }
}

/** Both clocks of the device at the moment a sync is prepared. */
private class SyncClocks(val monotonicMillis: Long, val wallMillis: Long)

/**
 * If the wall clock and the monotonic clock disagree by more than this about how long ago something was, the device
 * restarted in between (the monotonic clock starts at zero again) and the monotonic value is useless.
 */
private const val RESTART_TOLERANCE_MILLIS = 2 * 60_000L

/**
 * How long ago a Zeitpunkt was, as the server wants it (Sync-Protokoll, "Zeit als Alter"). Normally the monotonic
 * clock measures it, it does not jump. Lay a restart between the Zeitpunkt and now, the monotonic value is from the
 * previous boot: the age then comes from the wall clock (Zeitpunkt and now are both on that clock, so the offset to
 * the server clock known from the last snapshot cancels out; the server calculates "its time at receipt − age").
 */
private fun SyncClocks.ageOf(monotonicAt: Long, wallAt: Long): Long {
    val monotonicAge = monotonicMillis - monotonicAt
    val wallAge = wallMillis - wallAt
    val restarted = monotonicAge < 0 || kotlin.math.abs(monotonicAge - wallAge) > RESTART_TOLERANCE_MILLIS
    return (if (restarted) wallAge else monotonicAge).coerceAtLeast(0)
}

private fun OutboxSessionEntity.toUpdate(clocks: SyncClocks) = SessionUpdate(
    id = sessionId,
    startAgeMillis = clocks.ageOf(startMonotonicMillis, startWallMillis),
    endAgeMillis = if (endMonotonicMillis != null && endWallMillis != null) clocks.ageOf(endMonotonicMillis, endWallMillis) else null,
    endReason = endReason?.let { name -> SessionEndReason.entries.firstOrNull { it.name == name } },
)
