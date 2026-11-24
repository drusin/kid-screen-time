package io.github.drusin.kidscreentime.server

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** WebPush-Verschlüsselung (RFC 8291, aes128gcm nach RFC 8188) gegen das Beispiel aus RFC 8291, Abschnitt 5. */
class WebPushEncryptionTest {
    // RFC 8291, section 5 (values as base64url).
    private val plaintext = "V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24"
    private val uaPrivate = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94"
    private val uaPublic = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    private val authSecret = "BTBZMqHH6r4Tts7J_aSIgg"
    private val asPrivate = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"
    private val salt = "DGv6ra1nlYgDCS1FRnbzlw"
    private val body = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
        "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
        "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN"

    private fun unb64(s: String) = WebPushReceiver.unb64(s)

    private val message = unb64(plaintext)

    @Test
    fun `die Verschlüsselung mit festem Salt und Schlüssel ergibt genau den Body aus RFC 8291`() {
        val asPublic = unb64(body).copyOfRange(21, 86)
        val ephemeral = WebPushReceiver.keyPair(unb64(asPrivate), asPublic)

        val encrypted = WebPushEncryption.encrypt(message, uaPublic, authSecret, ephemeral, unb64(salt))

        assertContentEquals(unb64(body), encrypted)
    }

    @Test
    fun `der Empfänger entschlüsselt den Body aus RFC 8291 mit dem privaten Schlüssel des Geräts`() {
        val receiver = WebPushReceiver(WebPushReceiver.keyPair(unb64(uaPrivate), unb64(uaPublic)), unb64(authSecret))

        assertContentEquals(message, receiver.decrypt(unb64(body)))
    }

    @Test
    fun `jede Nachricht bekommt eigenen Salt und eigenen Schlüssel und der Empfänger liest sie`() {
        val receiver = WebPushReceiver()
        val text = "Mia hat noch 5 Minuten – äöü".toByteArray()

        val first = WebPushEncryption.encrypt(text, receiver.publicKeyBase64Url, receiver.authSecretBase64Url)
        val second = WebPushEncryption.encrypt(text, receiver.publicKeyBase64Url, receiver.authSecretBase64Url)

        assertContentEquals(text, receiver.decrypt(first))
        assertContentEquals(text, receiver.decrypt(second))
        assert(!first.contentEquals(second)) { "Zwei Nachrichten dürfen nie gleich verschlüsselt sein" }
    }

    @Test
    fun `ein anderes Gerät kann die Nachricht nicht lesen`() {
        val device = WebPushReceiver()
        val other = WebPushReceiver()

        val encrypted = WebPushEncryption.encrypt("geheim".toByteArray(), device.publicKeyBase64Url, device.authSecretBase64Url)

        assertFailsWith<Exception> { other.decrypt(encrypted) }
    }

    @Test
    fun `die längste Nachricht passt in einen Record und eine längere wird abgelehnt`() {
        val receiver = WebPushReceiver()
        val longest = ByteArray(WebPushEncryption.MAX_PLAINTEXT) { 'a'.code.toByte() }

        val encrypted = WebPushEncryption.encrypt(longest, receiver.publicKeyBase64Url, receiver.authSecretBase64Url)

        assertEquals(WebPushEncryption.RECORD_SIZE, encrypted.size)
        assertContentEquals(longest, receiver.decrypt(encrypted))
        assertFailsWith<IllegalArgumentException> {
            WebPushEncryption.encrypt(ByteArray(longest.size + 1), receiver.publicKeyBase64Url, receiver.authSecretBase64Url)
        }
    }

    @Test
    fun `ungültige Schlüssel werden erkannt`() {
        assertFailsWith<InvalidPushKeyException> { WebPushEncryption.parsePublicKey("kein base64url!") }
        assertFailsWith<InvalidPushKeyException> { WebPushEncryption.parsePublicKey("AAAA") }
        // Right size and prefix, but not a point of the curve.
        val offCurve = WebPushReceiver.b64(byteArrayOf(4) + ByteArray(64) { 1 })
        assertFailsWith<InvalidPushKeyException> { WebPushEncryption.parsePublicKey(offCurve) }
        assertFailsWith<InvalidPushKeyException> { WebPushEncryption.parseAuthSecret("AAAA") }
    }
}
