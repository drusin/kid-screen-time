package io.github.drusin.kidscreentime.server

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The receiving side of WebPush (RFC 8291) for tests: what the UnifiedPush connector does on the device. Written
 * separately from [WebPushEncryption] so an encryption mistake cannot cancel out in a round trip.
 */
class WebPushReceiver(val keyPair: KeyPair = WebPushEncryption.generateEphemeralKey(), val authSecret: ByteArray = randomAuth()) {
    private val uaPublic = point(keyPair.public as ECPublicKey)

    /** What the device registers with the server. */
    val publicKeyBase64Url: String = b64(uaPublic)
    val authSecretBase64Url: String = b64(authSecret)

    fun decrypt(body: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(body)
        val salt = ByteArray(16).also(buffer::get)
        val recordSize = buffer.getInt()
        val idLength = buffer.get().toInt() and 0xff
        check(idLength == 65) { "Schlüssel-ID-Länge $idLength" }
        check(recordSize >= body.size - 86) { "Record Size $recordSize zu klein" }
        val asPublic = ByteArray(65).also(buffer::get)
        val ecdh = KeyAgreement.getInstance("ECDH").run {
            init(keyPair.private)
            doPhase(KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(pointOf(asPublic), CURVE)), true)
            generateSecret()
        }
        val ikm = hmac(hmac(authSecret, ecdh), "WebPush: info\u0000".toByteArray() + uaPublic + asPublic + byteArrayOf(1))
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm\u0000".toByteArray() + byteArrayOf(1)).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce\u0000".toByteArray() + byteArrayOf(1)).copyOf(12)
        val padded = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
            doFinal(body, 86, body.size - 86)
        }
        val end = padded.indexOfLast { it != 0.toByte() }
        check(end >= 0 && padded[end] == 2.toByte()) { "Kein Padding-Trenner" }
        return padded.copyOf(end)
    }

    companion object {
        val CURVE: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }

        private fun randomAuth() = ByteArray(16).also(java.security.SecureRandom()::nextBytes)

        fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun unb64(text: String): ByteArray = Base64.getUrlDecoder().decode(text)

        fun pointOf(uncompressed: ByteArray) =
            ECPoint(BigInteger(1, uncompressed.copyOfRange(1, 33)), BigInteger(1, uncompressed.copyOfRange(33, 65)))

        /** A key pair from the raw private scalar and the uncompressed public point, as RFC 8291 lists them. */
        fun keyPair(privateScalar: ByteArray, uncompressedPublic: ByteArray): KeyPair {
            val factory = KeyFactory.getInstance("EC")
            return KeyPair(
                factory.generatePublic(ECPublicKeySpec(pointOf(uncompressedPublic), CURVE)),
                factory.generatePrivate(ECPrivateKeySpec(BigInteger(1, privateScalar), CURVE)),
            )
        }

        private fun point(key: ECPublicKey): ByteArray {
            fun fixed(v: BigInteger) = v.toByteArray().dropWhile { it == 0.toByte() }.toByteArray().let { ByteArray(32 - it.size) + it }
            return byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)
        }

        private fun hmac(key: ByteArray, data: ByteArray) =
            Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }
    }
}
