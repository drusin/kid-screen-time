package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.domain.clock.WallClock
import java.security.SecureRandom
import java.time.Duration
import java.util.UUID

/** A freshly created Kopplungscode. The plain [code] exists only here; the database keeps its hash. */
data class CreatedPairingCode(val code: String, val expiresAtEpochMillis: Long)

sealed interface RedeemResult {
    data class Paired(val response: RedeemPairingCodeResponse) : RedeemResult

    /** Unknown, expired or already used. The three cases are deliberately indistinguishable. */
    data object InvalidCode : RedeemResult
}

/** Creating and redeeming Kopplungscodes (einmalig, 10 minutes valid, stored only as hash). */
class PairingService(
    private val database: Database,
    private val wallClock: WallClock,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Creates a Kopplungscode. For a [DeviceRole.CHILD] code give either [childId] (existing Kind) or
     * [newChildName] (the Kind is created when the code is redeemed). A [DeviceRole.PARENT] code needs neither.
     */
    fun createCode(role: DeviceRole, childId: String? = null, newChildName: String? = null): CreatedPairingCode {
        require((role == DeviceRole.CHILD) || (childId == null && newChildName == null)) { "Elterngeräte gehören zu keinem Kind" }
        require(role == DeviceRole.PARENT || (childId != null) != (newChildName != null)) {
            "Kindgerät braucht genau eines: Kind oder Name eines neuen Kindes"
        }
        val now = wallClock.now().toEpochMilli()
        val expiresAt = now + VALIDITY.toMillis()
        val code = randomCode()
        database.transaction { c ->
            c.prepareStatement(
                "INSERT INTO pairing_codes (id, code_hash, role, child_id, new_child_name, created_at, expires_at) VALUES (?,?,?,?,?,?,?)",
            ).use {
                it.setString(1, UUID.randomUUID().toString())
                it.setString(2, sha256Hex(code))
                it.setString(3, role.name)
                it.setString(4, childId)
                it.setString(5, newChildName)
                it.setLong(6, now)
                it.setLong(7, expiresAt)
                it.executeUpdate()
            }
        }
        return CreatedPairingCode(formatCode(code), expiresAt)
    }

    /** Redeems [rawCode] exactly once: marks it used, creates the Gerät with a new Geräte-Token. */
    fun redeem(rawCode: String, deviceName: String, push: PushRegistration?): RedeemResult {
        val code = normalize(rawCode)
        val now = wallClock.now().toEpochMilli()
        return database.transaction { c ->
            val found = c.prepareStatement(
                "SELECT id, role, child_id, new_child_name FROM pairing_codes " +
                    "WHERE code_hash = ? AND redeemed_at IS NULL AND expires_at > ?",
            ).use { st ->
                st.setString(1, sha256Hex(code))
                st.setLong(2, now)
                st.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else Found(rs.getString(1), DeviceRole.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4))
                }
            } ?: return@transaction RedeemResult.InvalidCode

            c.prepareStatement("UPDATE pairing_codes SET redeemed_at = ? WHERE id = ?").use {
                it.setLong(1, now)
                it.setString(2, found.id)
                it.executeUpdate()
            }

            var childId = found.childId
            var childName: String? = null
            if (found.newChildName != null) {
                childId = UUID.randomUUID().toString()
                childName = found.newChildName
                c.prepareStatement("INSERT INTO children (id, name, created_at) VALUES (?,?,?)").use {
                    it.setString(1, childId)
                    it.setString(2, childName)
                    it.setLong(3, now)
                    it.executeUpdate()
                }
            } else if (childId != null) {
                childName = c.prepareStatement("SELECT name FROM children WHERE id = ?").use { st ->
                    st.setString(1, childId)
                    st.executeQuery().use { rs -> rs.next(); rs.getString(1) }
                }
            }

            val deviceId = UUID.randomUUID().toString()
            val token = newDeviceToken(random)
            c.prepareStatement(
                "INSERT INTO devices (id, name, role, child_id, token_hash, paired_at, push_endpoint, push_public_key, push_auth_secret) " +
                    "VALUES (?,?,?,?,?,?,?,?,?)",
            ).use {
                it.setString(1, deviceId)
                it.setString(2, deviceName)
                it.setString(3, found.role.name)
                it.setString(4, childId)
                it.setString(5, sha256Hex(token))
                it.setLong(6, now)
                it.setString(7, push?.endpoint)
                it.setString(8, push?.publicKey)
                it.setString(9, push?.authSecret)
                it.executeUpdate()
            }
            RedeemResult.Paired(RedeemPairingCodeResponse(deviceId, token, found.role, childId, childName))
        }
    }

    private class Found(val id: String, val role: DeviceRole, val childId: String?, val newChildName: String?)

    private fun randomCode(): String = buildString { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    companion object {
        /** A Kopplungscode is valid for 10 minutes. */
        val VALIDITY: Duration = Duration.ofMinutes(10)

        // No 0/O, 1/I/L: the code is also typed by hand.
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 8

        /** `ABCD-EFGH` for display. */
        fun formatCode(code: String) = code.chunked(4).joinToString("-")

        /** Accepts lower case, spaces and dashes when typed by hand. */
        fun normalize(raw: String) = raw.filter { it.isLetterOrDigit() }.uppercase()
    }
}
