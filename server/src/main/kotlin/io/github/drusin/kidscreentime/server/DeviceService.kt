package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.DeviceInfo
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.domain.clock.WallClock

/** An active (not entkoppelt) Gerät as seen by the server. */
data class Device(val id: String, val name: String, val role: DeviceRole, val childId: String?)

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
}
