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
    /** The Wochenvorlage version that is valid tomorrow (differs from [weeklyTemplate] after a change today). */
    val weeklyTemplateTomorrow: WeeklyTemplateVersion? = null,
    /** The Tagesanpassung "Letzte zurücknehmen" would take back (its delta in minutes), null when there is none today. */
    val lastAdjustmentMinutes: Int? = null,
    /** The "letzte Tage" of the Startseite: the days before today, newest first (at most 7, none before the Kind existed). */
    val recentDays: List<RecentDay> = emptyList(),
)

/** One of the "letzten Tage": "genutzt / Kontingent", with the Überziehung marked. */
@Serializable
data class RecentDay(val day: String, val allowance: AllowanceBreakdown, val usedMillis: Long, val overrunMillis: Long)

/** A Sitzung (the part of it that falls on one [HistoryDay]) in the Verlauf. Times are epoch millis, cut at midnight. */
@Serializable
data class HistorySession(
    val id: String,
    val startEpochMillis: Long,
    /** Null while the Sitzung still runs (today only). */
    val endEpochMillis: Long? = null,
    val durationMillis: Long,
    /** The Sitzung began before 00:00 of this day. */
    val fromPreviousDay: Boolean = false,
    /** The Sitzung runs past 00:00 of the next day. */
    val continuesNextDay: Boolean = false,
    /** Why the Sitzung ended, only on the day it ended. [SessionEndReason.AUTOMATIC_STOP] is "Automatisch gestoppt". */
    val endReason: SessionEndReason? = null,
    /** "offline erfasst". */
    val recordedOffline: Boolean = false,
)

/** A Tagesanpassung in the Verlauf. [takenBack]: a later Gegenbuchung cancelled it; [reversal]: this one is such a Gegenbuchung. */
@Serializable
data class HistoryAdjustment(
    val deltaMinutes: Int,
    val bookedAtEpochMillis: Long,
    val reversal: Boolean = false,
    val takenBack: Boolean = false,
)

/** One day of the Verlauf, as `domain` calculates it. [allowance] is the Tageskontingent of that day. */
@Serializable
data class HistoryDay(
    val day: String,
    val allowance: AllowanceBreakdown,
    val usedMillis: Long,
    val overrunMillis: Long,
    val sessions: List<HistorySession> = emptyList(),
    val adjustments: List<HistoryAdjustment> = emptyList(),
)

/** The complete Verlauf of a Kind (answer of [ApiPaths.PARENT_HISTORY]), newest day first, including today. */
@Serializable
data class HistoryResponse(val childId: String, val timeZone: String, val days: List<HistoryDay>)

/** A Tagesanpassung for today: [deltaMinutes] is the Zu- (+) or Abschlag (−) in minutes, never 0. */
@Serializable
data class DailyAdjustmentRequest(val childId: String, val deltaMinutes: Int)

/** Takes back the last Tagesanpassung of today. */
@Serializable
data class UndoDailyAdjustmentRequest(val childId: String)

/** New Wochenvorlage: [minutes] for Monday to Sunday (7 values). Valid from tomorrow. */
@Serializable
data class WeeklyTemplateRequest(val childId: String, val minutes: List<Int>)

/** New rules of a Kind: [name], Vorwarnschwelle and Überziehungsgrenze in minutes. */
@Serializable
data class RulesRequest(val childId: String, val name: String, val warningThresholdMinutes: Int, val overrunLimitMinutes: Int)

/** Unpairs the Gerät [deviceId] (an entry of the Geräteliste). Afterwards its Geräte-Token answers `401`. */
@Serializable
data class UnpairDeviceRequest(val deviceId: String)

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
