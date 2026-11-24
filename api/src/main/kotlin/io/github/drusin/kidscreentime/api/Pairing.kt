package io.github.drusin.kidscreentime.api

import kotlinx.serialization.Serializable

/** Role of a device. It is fixed by the Kopplung and never changes afterwards. */
@Serializable
enum class DeviceRole { PARENT, CHILD }

/** UnifiedPush endpoint and WebPush keys of a device. Optional until the push ticket sends them. */
@Serializable
data class PushRegistration(val endpoint: String, val publicKey: String, val authSecret: String)

/** Request to redeem a Kopplungscode (no token). */
@Serializable
data class RedeemPairingCodeRequest(
    val code: String,
    val deviceName: String,
    val push: PushRegistration? = null,
)

/** Answer of a successful Kopplung. [deviceToken] is shown only once and must be stored securely. */
@Serializable
data class RedeemPairingCodeResponse(
    val deviceId: String,
    val deviceToken: String,
    val role: DeviceRole,
    val childId: String? = null,
    val childName: String? = null,
)

/**
 * Request of a Elterngerät to create a Kopplungscode. For [DeviceRole.CHILD] give exactly one of [childId]
 * (existing Kind) and [newChildName] (only while there is no Kind yet; the Kind is created at the Kopplung).
 * For [DeviceRole.PARENT] give neither.
 */
@Serializable
data class CreatePairingCodeRequest(
    val role: DeviceRole,
    val childId: String? = null,
    val newChildName: String? = null,
)

/**
 * A new Kopplungscode, valid until [expiresAtEpochMillis] (server time). [serverTimeEpochMillis] lets the
 * device count down without trusting its own wall clock. [replacesDeviceName] is set when the Kind already
 * has a Kindgerät: the new one replaces it (Ersetzen-Hinweis). Like every Eltern-Aktion it returns the new
 * [snapshot].
 */
@Serializable
data class CreatePairingCodeResponse(
    val code: String,
    val expiresAtEpochMillis: Long,
    val serverTimeEpochMillis: Long,
    val serverUrl: String,
    val role: DeviceRole,
    val childId: String? = null,
    val childName: String? = null,
    val replacesDeviceName: String? = null,
    val snapshot: ParentSnapshot,
)
