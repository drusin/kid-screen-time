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
