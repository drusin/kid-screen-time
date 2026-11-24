package io.github.drusin.kidscreentime.api

import kotlinx.serialization.Serializable

/** One entry of the Geräteliste. */
@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val role: DeviceRole,
    val childId: String? = null,
    val pairedAtEpochMillis: Long,
    val lastSeenEpochMillis: Long? = null,
)

/**
 * A Kind in the Eltern-Snapshot: the same [status] the Kindgerät shows, plus the rules. Later tickets add
 * "Stand bekannt seit", Checkliste and Verlauf (optional fields).
 */
@Serializable
data class ParentChildSnapshot(
    val childId: String,
    val name: String,
    val status: ChildStatus? = null,
    val warningThresholdMinutes: Int? = null,
    val overrunLimitMinutes: Int? = null,
    /** The Wochenvorlage version that is valid today. */
    val weeklyTemplate: WeeklyTemplateVersion? = null,
    /** The Tagesanpassung "Letzte zurücknehmen" would take back (its delta in minutes), null when there is none today. */
    val lastAdjustmentMinutes: Int? = null,
)

/** A Tagesanpassung for today: [deltaMinutes] is the Zu- (+) or Abschlag (−) in minutes, never 0. */
@Serializable
data class DailyAdjustmentRequest(val childId: String, val deltaMinutes: Int)

/** Takes back the last Tagesanpassung of today. */
@Serializable
data class UndoDailyAdjustmentRequest(val childId: String)

/** Complete state for a Elterngerät. Always the full state, never deltas. */
@Serializable
data class ParentSnapshot(
    val serverTimeEpochMillis: Long,
    /** The Familien-Zeitzone (IANA id), for showing times such as "Sitzung läuft seit …". */
    val timeZone: String? = null,
    val devices: List<DeviceInfo>,
    val children: List<ParentChildSnapshot> = emptyList(),
)

/** Body of error answers (`400`, `429`). `401` and `403` carry no body. */
@Serializable
data class ErrorResponse(val message: String)
