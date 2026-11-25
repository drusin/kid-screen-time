package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.DeviceInfo
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.domain.clock.WallClock

/** An active (not entkoppelt) Gerät as seen by the server. */
data class Device(val id: String, val name: String, val role: DeviceRole, val childId: String?)

sealed interface UnpairResult {
    /** The Gerät is entkoppelt now. [device] is what it was. */
    data class Unpaired(val device: Device) : UnpairResult

    /** No active Gerät with this id (unknown or already entkoppelt). */
    data object UnknownDevice : UnpairResult

    /** The last Elterngerät stays, so the family cannot lock itself out. */
    data object LastParentDevice : UnpairResult
}

class DeviceService(private val database: Database, private val wallClock: WallClock) {
    /**
     * Finds the active Gerät for a Bearer token and records "zuletzt gesehen".
     * Returns null for unknown tokens and for entkoppelte Geräte (the API answers `401`).
     */
    fun authenticate(token: String): Device? {
        val now = wallClock.now().toEpochMilli()
        return database.transaction { c ->
            val device = c.prepareStatement(
                "SELECT id, name, role, child_id FROM devices WHERE token_hash = ? AND unpaired_at IS NULL",
            ).use { st ->
                st.setString(1, sha256Hex(token))
                st.executeQuery().use { rs ->
                    if (rs.next()) Device(rs.getString(1), rs.getString(2), DeviceRole.valueOf(rs.getString(3)), rs.getString(4)) else null
                }
            } ?: return@transaction null
            c.prepareStatement("UPDATE devices SET last_seen_at = ? WHERE id = ?").use {
                it.setLong(1, now)
                it.setString(2, device.id)
                it.executeUpdate()
            }
            device
        }
    }

    /** The Geräteliste: all active Geräte, oldest Kopplung first. */
    fun listActive(): List<DeviceInfo> = database.transaction { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, name, role, child_id, paired_at, last_seen_at FROM devices WHERE unpaired_at IS NULL ORDER BY paired_at, id",
            ).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            DeviceInfo(
                                id = rs.getString(1),
                                name = rs.getString(2),
                                role = DeviceRole.valueOf(rs.getString(3)),
                                childId = rs.getString(4),
                                pairedAtEpochMillis = rs.getLong(5),
                                lastSeenEpochMillis = rs.getLong(6).takeUnless { rs.wasNull() },
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Unpairs the active Gerät [deviceId] (sets the entkoppelt-Markierung; its token answers `401` from now on, and it gets
     * no push signals). The last Elterngerät is refused. One transaction. The caller sends the signals.
     */
    fun unpair(deviceId: String): UnpairResult {
        val now = wallClock.now().toEpochMilli()
        return database.transaction { c ->
            val device = c.prepareStatement("SELECT id, name, role, child_id FROM devices WHERE id = ? AND unpaired_at IS NULL").use { st ->
                st.setString(1, deviceId)
                st.executeQuery().use { rs ->
                    if (rs.next()) Device(rs.getString(1), rs.getString(2), DeviceRole.valueOf(rs.getString(3)), rs.getString(4)) else null
                }
            } ?: return@transaction UnpairResult.UnknownDevice
            if (device.role == DeviceRole.PARENT && activeParentCount(c) <= 1) return@transaction UnpairResult.LastParentDevice
            unpair(c, device.id, now)
            UnpairResult.Unpaired(device)
        }
    }

    private fun activeParentCount(c: java.sql.Connection): Int =
        c.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM devices WHERE role = 'PARENT' AND unpaired_at IS NULL").use { rs -> rs.next(); rs.getInt(1) }
        }

    companion object {
        /** Marks the Gerät entkoppelt, inside the caller's transaction (also used when a new Kindgerät replaces the old one). */
        fun unpair(c: java.sql.Connection, deviceId: String, now: Long) {
            c.prepareStatement("UPDATE devices SET unpaired_at = ? WHERE id = ? AND unpaired_at IS NULL").use {
                it.setLong(1, now)
                it.setString(2, deviceId)
                it.executeUpdate()
            }
        }
    }
}
