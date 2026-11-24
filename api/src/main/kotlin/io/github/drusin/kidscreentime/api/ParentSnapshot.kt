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

/** A Kind in the Eltern-Snapshot. Later tickets add the status, Wochenvorlage and Verlauf (optional fields). */
@Serializable
data class ParentChildSnapshot(val childId: String, val name: String)

/** Complete state for a Elterngerät. Always the full state, never deltas. */
@Serializable
data class ParentSnapshot(
    val serverTimeEpochMillis: Long,
    val devices: List<DeviceInfo>,
    val children: List<ParentChildSnapshot> = emptyList(),
)

/** Body of error answers (`400`, `429`). `401` and `403` carry no body. */
@Serializable
data class ErrorResponse(val message: String)
