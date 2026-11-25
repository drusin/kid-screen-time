package io.github.drusin.kidscreentime.pairing

import android.content.Context
import android.content.SharedPreferences
import io.github.drusin.kidscreentime.api.DeviceRole

/** What the app knows about its own Kopplung (not secret; the Geräte-Token lives in the [TokenStore]). */
data class PairedDevice(
    val serverUrl: String,
    val deviceId: String,
    val deviceName: String,
    val role: DeviceRole,
    val childId: String? = null,
    val childName: String? = null,
)

/** Persists the [PairedDevice] in private SharedPreferences. */
class PairingStorage(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("pairing", Context.MODE_PRIVATE))

    fun load(): PairedDevice? {
        val role = prefs.getString("role", null)?.let { runCatching { DeviceRole.valueOf(it) }.getOrNull() } ?: return null
        return PairedDevice(
            serverUrl = prefs.getString("serverUrl", null) ?: return null,
            deviceId = prefs.getString("deviceId", null) ?: return null,
            deviceName = prefs.getString("deviceName", null) ?: return null,
            role = role,
            childId = prefs.getString("childId", null),
            childName = prefs.getString("childName", null),
        )
    }

    /** The server entkoppelt this device: the Kopplung is gone, only the hint "Gerät wurde entkoppelt" stays until it is acknowledged. */
    fun markRevoked() {
        prefs.edit().clear().putBoolean("revoked", true).apply()
    }

    fun isRevoked(): Boolean = prefs.getBoolean("revoked", false)

    fun save(device: PairedDevice) {
        prefs.edit()
            .remove("revoked")
            .putString("serverUrl", device.serverUrl)
            .putString("deviceId", device.deviceId)
            .putString("deviceName", device.deviceName)
            .putString("role", device.role.name)
            .putString("childId", device.childId)
            .putString("childName", device.childName)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
