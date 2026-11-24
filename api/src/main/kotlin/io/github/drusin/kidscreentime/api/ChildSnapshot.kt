package io.github.drusin.kidscreentime.api

import kotlinx.serialization.Serializable

/** Zustand des Tages eines Kindes, wie `domain` ihn berechnet. */
@Serializable
enum class ChildState { READY, RUNNING, WARNING, OVERRUN, EXHAUSTED }

/** Tageskontingent mit Aufschlüsselung: Wochenvorlage plus Tagesanpassungen ([totalMinutes] ist nie < 0). */
@Serializable
data class AllowanceBreakdown(val templateMinutes: Int, val adjustmentMinutes: Int, val totalMinutes: Int)

@Serializable
data class RunningSession(val id: String, val startEpochMillis: Long)

/** The values `domain` derives for one moment. Server-side they are computed at the server time of the snapshot. */
@Serializable
data class ChildStatus(
    /** The Tag as ISO date in the Familien-Zeitzone. */
    val day: String,
    val allowance: AllowanceBreakdown,
    val usedMillis: Long,
    val remainingMillis: Long,
    val overrunMillis: Long,
    val state: ChildState,
    val runningSession: RunningSession? = null,
)

/** One version of the Wochenvorlage: [minutes] for Monday to Sunday, valid from [validFrom] (ISO date) on. */
@Serializable
data class WeeklyTemplateVersion(val validFrom: String, val minutes: List<Int>)

@Serializable
enum class SessionEndReason { STOPPED, AUTOMATIC_STOP }

/** A Sitzung touching today, as input for the local calculation at the Kindgerät. */
@Serializable
data class SessionRecord(
    val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val endReason: SessionEndReason? = null,
    /** The server learned of the start only with a delay ("offline erfasst", Sync-Protokoll). */
    val recordedOffline: Boolean = false,
)

/** A Tagesanpassung of today. */
@Serializable
data class DayAdjustment(val day: String, val deltaMinutes: Int, val bookedAtEpochMillis: Long)

/**
 * Complete state for a Kindgerät. Always the full state, never deltas. Besides the derived [status] it holds
 * everything `domain` needs at the device to calculate on its own until midnight and beyond: the Wochenvorlage
 * versions valid today and tomorrow, today's Sitzungen and Tagesanpassungen, and the rules.
 */
@Serializable
data class ChildSnapshot(
    val childId: String,
    val childName: String,
    val serverTimeEpochMillis: Long,
    val timeZone: String,
    val status: ChildStatus,
    val warningThresholdMinutes: Int,
    val overrunLimitMinutes: Int,
    val templateToday: WeeklyTemplateVersion,
    val templateTomorrow: WeeklyTemplateVersion,
    val todaySessions: List<SessionRecord> = emptyList(),
    val todayAdjustments: List<DayAdjustment> = emptyList(),
)

/**
 * The whole state of one Sitzung as the Kindgerät knows it (an upsert, never an event): [id] is the UUID the device
 * made at the start. Points in time are sent as age, measured with the monotonic clock at the moment of sending; the
 * server calculates "server time at receipt − age". A stop always brings its start with it ([endAgeMillis] set).
 */
@Serializable
data class SessionUpdate(val id: String, val startAgeMillis: Long, val endAgeMillis: Long? = null)

/** Body of the sync (`POST` [ApiPaths.CHILD_SYNC]): the open Sitzungen of the Outbox, in start order. May be empty. */
@Serializable
data class ChildSyncRequest(val sessions: List<SessionUpdate> = emptyList())
