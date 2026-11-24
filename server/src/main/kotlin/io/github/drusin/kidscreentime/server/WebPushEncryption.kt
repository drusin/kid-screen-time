package io.github.drusin.kidscreentime.server

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The public key or auth secret a device registered is malformed (wrong size, not a point on P-256, bad base64url). */
class InvalidPushKeyException(message: String) : IllegalArgumentException(message)

/**
 * WebPush message encryption (RFC 8291) with the `aes128gcm` content coding (RFC 8188), written with the JCA only.
 * A push message is one record, so one call produces the whole body for the push service.
 *
 * Why our own code: the established JVM libraries (Tink `apps-webpush`, `web-push` with BouncyCastle) pull in a large
 * dependency tree (protobuf, BouncyCastle) for ~60 lines of HKDF, ECDH and AES-GCM, and the fat JAR stays small.
 * `WebPushEncryptionTest` pins the implementation to the example of RFC 8291, section 5.
 */
object WebPushEncryption {
    private const val PUBLIC_KEY_SIZE = 65
    private const val AUTH_SECRET_SIZE = 16
    private const val SALT_SIZE = 16
    private const val TAG_BYTES = 16

    /** Record size announced in the header. Fixed at the maximum a push service must support (RFC 8291, section 4). */
    const val RECORD_SIZE = 4096

    /** Header of the coding: salt, record size, key id length and the key id (the sender's public key). */
    const val HEADER_SIZE = SALT_SIZE + 4 + 1 + PUBLIC_KEY_SIZE

    /** Longest plaintext that fits into the single record. */
    const val MAX_PLAINTEXT = RECORD_SIZE - HEADER_SIZE - TAG_BYTES - 1

    private val curve: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }
    private val base64UrlDecoder = Base64.getUrlDecoder()
    private val secureRandom = SecureRandom()

    /** Decodes a registration's public key (uncompressed P-256 point, base64url) and checks it lies on the curve. */
    fun parsePublicKey(base64UrlKey: String): ByteArray {
        val bytes = decode(base64UrlKey, "Öffentlicher Schlüssel")
        if (bytes.size != PUBLIC_KEY_SIZE || bytes[0] != 0x04.toByte()) {
            throw InvalidPushKeyException("Öffentlicher Schlüssel hat nicht die Form eines unkomprimierten P-256-Punkts")
        }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        val p = (curve.curve.field as ECFieldFp).p
        val onCurve = x < p && y < p &&
            y.modPow(BigInteger.TWO, p) == (x.pow(3) + curve.curve.a * x + curve.curve.b).mod(p)
        if (!onCurve) throw InvalidPushKeyException("Öffentlicher Schlüssel liegt nicht auf der Kurve P-256")
        return bytes
    }

    fun parseAuthSecret(base64UrlSecret: String): ByteArray {
        val bytes = decode(base64UrlSecret, "Auth-Secret")
        if (bytes.size != AUTH_SECRET_SIZE) throw InvalidPushKeyException("Auth-Secret muss $AUTH_SECRET_SIZE Byte lang sein")
        return bytes
    }

    private fun decode(value: String, what: String): ByteArray =
        try {
            base64UrlDecoder.decode(value.trimEnd('='))
        } catch (_: IllegalArgumentException) {
            throw InvalidPushKeyException("$what ist kein base64url")
        }

    /**
     * Encrypts [plaintext] for the device with [publicKey] and [authSecret] (as registered, base64url).
     * [ephemeral] and [salt] are random per message; tests pass fixed values to compare with the RFC example.
     */
    fun encrypt(
        plaintext: ByteArray,
        publicKey: String,
        authSecret: String,
        ephemeral: KeyPair = generateEphemeralKey(),
        salt: ByteArray = ByteArray(SALT_SIZE).also(secureRandom::nextBytes),
    ): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT) { "Nachricht zu lang für einen Record" }
        require(salt.size == SALT_SIZE) { "Salt muss $SALT_SIZE Byte lang sein" }
        val uaPublic = parsePublicKey(publicKey)
        val auth = parseAuthSecret(authSecret)
        val asPublic = encodePoint((ephemeral.public as ECPublicKey).w)

        val ecdhSecret = KeyAgreement.getInstance("ECDH").run {
            init(ephemeral.private)
            doPhase(decodePublicKey(uaPublic), true)
            generateSecret()
        }
        // RFC 8291, section 3.4: IKM from the ECDH secret and the auth secret, bound to both public keys.
        val prkKey = hmac(auth, ecdhSecret)
        val keyInfo = "WebPush: info".toByteArray() + 0 + uaPublic + asPublic
        val ikm = hmac(prkKey, keyInfo + 1)
        // RFC 8188, sections 2.2 and 2.3: content encryption key and nonce from IKM and salt.
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + 0 + 1).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + 0 + 1).copyOf(12)

        // One record, so it is the last one: the padding delimiter is 0x02 (RFC 8188, section 2).
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
        }
        val encrypted = cipher.doFinal(plaintext + 2)
        return ByteBuffer.allocate(HEADER_SIZE + encrypted.size)
            .put(salt).putInt(RECORD_SIZE).put(PUBLIC_KEY_SIZE.toByte()).put(asPublic).put(encrypted)
            .array()
    }

    fun generateEphemeralKey(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), secureRandom) }.generateKeyPair()

    private fun decodePublicKey(point: ByteArray) = KeyFactory.getInstance("EC").generatePublic(
        ECPublicKeySpec(
            ECPoint(BigInteger(1, point.copyOfRange(1, 33)), BigInteger(1, point.copyOfRange(33, 65))),
            curve,
        ),
    )

    private fun encodePoint(point: ECPoint): ByteArray =
        byteArrayOf(0x04) + unsigned32(point.affineX) + unsigned32(point.affineY)

    private fun unsigned32(value: BigInteger): ByteArray {
        val raw = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - raw.size) + raw
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private operator fun ByteArray.plus(byte: Int): ByteArray = this + byte.toByte()
}
