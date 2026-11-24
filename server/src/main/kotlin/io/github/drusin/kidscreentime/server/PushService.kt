package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.api.PushSignal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/** Answer of the push service (ntfy) to a publish. */
sealed interface PublishResult {
    data object Delivered : PublishResult

    /** `404`/`410`: the topic is gone (nobody subscribes for days). The registration is useless. */
    data object Gone : PublishResult

    /** Network error or any other status. The signal is dropped; the next sync of the device heals it. */
    data object Failed : PublishResult
}

/** Posts one already encrypted body to a push endpoint. An interface so tests and later tickets can observe it. */
fun interface PushPublisher {
    fun publish(url: String, body: ByteArray): PublishResult
}

/** The real publisher: a plain `POST` with the WebPush headers to the internal ntfy URL. */
class HttpPushPublisher(private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) : PushPublisher {
    override fun publish(url: String, body: ByteArray): PublishResult = try {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/octet-stream")
            .header("Content-Encoding", "aes128gcm")
            .header("TTL", TTL_SECONDS.toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        when (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()) {
            in 200..299 -> PublishResult.Delivered
            404, 410 -> PublishResult.Gone
            else -> PublishResult.Failed
        }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        PublishResult.Failed
    } catch (_: Exception) {
        PublishResult.Failed
    }

    private companion object {
        /** A Resync-Signal is worthless after a while; ntfy keeps it at most its cache duration anyway. */
        const val TTL_SECONDS = 3600
    }
}

/** Why a push registration is refused. */
class InvalidPushRegistrationException(message: String) : IllegalArgumentException(message)

/**
 * Resync-Signale over ntfy (UnifiedPush). Every message is encrypted end-to-end for the one device with the keys it
 * registered (WebPush, RFC 8291), so ntfy only ever sees ciphertext. The server publishes to the internal ntfy URL;
 * the devices' endpoints name the public URL, which is replaced by the internal one.
 *
 * Publishing never blocks a request: signals go through one background thread. A failed publish is dropped (a
 * Resync-Signal carries no state). Tests call [flush] to wait for the queue.
 */
class PushService(
    private val database: Database,
    private val config: ServerConfig,
    private val publisher: PushPublisher = HttpPushPublisher(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "push-publisher").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(PushService::class.java)

    /**
     * Checks a registration. The endpoint must be a topic of our own ntfy (`<public ntfy URL>/up…`): the server
     * posts to it, so an arbitrary URL would turn it into a request relay. Keys must be well-formed P-256/auth values.
     */
    fun validate(registration: PushRegistration) {
        internalUrl(registration.endpoint) ?: throw InvalidPushRegistrationException("Der Endpoint gehört nicht zum ntfy dieses Servers")
        try {
            WebPushEncryption.parsePublicKey(registration.publicKey)
            WebPushEncryption.parseAuthSecret(registration.authSecret)
        } catch (e: InvalidPushKeyException) {
            throw InvalidPushRegistrationException(e.message ?: "Schlüssel ungültig")
        }
    }

    /** Stores the (already validated) registration of the Gerät [deviceId]. */
    fun register(deviceId: String, registration: PushRegistration) {
        database.transaction { c ->
            c.prepareStatement("UPDATE devices SET push_endpoint = ?, push_public_key = ?, push_auth_secret = ? WHERE id = ?").use {
                it.setString(1, registration.endpoint)
                it.setString(2, registration.publicKey)
                it.setString(3, registration.authSecret)
                it.setString(4, deviceId)
                it.executeUpdate()
            }
        }
    }

    /** Resync-Signal to the active Kindgerät of [childId]. */
    fun signalChild(childId: String, signal: PushSignal = PushSignal()) = enqueue(recipients("role = 'CHILD' AND child_id = ?", childId), signal)

    /** Resync-Signal to all active Elterngeräte. */
    fun signalParents(signal: PushSignal = PushSignal()) = enqueue(recipients("role = 'PARENT'"), signal)

    /** Waits until everything queued so far was published. For tests. */
    fun flush() {
        executor.submit {}.get()
    }

    override fun close() {
        executor.shutdown()
    }

    private class Recipient(val deviceId: String, val registration: PushRegistration)

    private fun recipients(condition: String, vararg args: String): List<Recipient> = database.transaction { c ->
        c.prepareStatement(
            "SELECT id, push_endpoint, push_public_key, push_auth_secret FROM devices " +
                "WHERE unpaired_at IS NULL AND push_endpoint IS NOT NULL AND push_public_key IS NOT NULL AND push_auth_secret IS NOT NULL AND $condition ORDER BY paired_at, id",
        ).use { st ->
            args.forEachIndexed { index, value -> st.setString(index + 1, value) }
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Recipient(rs.getString(1), PushRegistration(rs.getString(2), rs.getString(3), rs.getString(4)))) }
            }
        }
    }

    private fun enqueue(recipients: List<Recipient>, signal: PushSignal) {
        if (recipients.isEmpty()) return
        val plaintext = payload(signal)
        executor.execute { recipients.forEach { deliver(it, plaintext) } }
    }

    /** The JSON of [signal]; the text is dropped when it would not fit comfortably (ntfy limits a message to 4 KB). */
    private fun payload(signal: PushSignal): ByteArray {
        val full = ApiJson.encodeToString(signal).toByteArray()
        return if (full.size <= MAX_PAYLOAD_BYTES) full else ApiJson.encodeToString(PushSignal()).toByteArray()
    }

    private fun deliver(recipient: Recipient, plaintext: ByteArray) {
        val url = internalUrl(recipient.registration.endpoint) ?: return
        val body = try {
            WebPushEncryption.encrypt(plaintext, recipient.registration.publicKey, recipient.registration.authSecret)
        } catch (e: Exception) {
            log.warn("Push an Gerät {} nicht verschlüsselbar: {}", recipient.deviceId, e.message)
            return
        }
        when (publisher.publish(url, body)) {
            PublishResult.Delivered -> Unit
            PublishResult.Gone -> {
                log.info("Push-Topic von Gerät {} ist weg, Registrierung entfernt", recipient.deviceId)
                database.transaction { c ->
                    c.prepareStatement("UPDATE devices SET push_endpoint = NULL, push_public_key = NULL, push_auth_secret = NULL WHERE id = ?").use {
                        it.setString(1, recipient.deviceId)
                        it.executeUpdate()
                    }
                }
            }
            PublishResult.Failed -> log.warn("Push an Gerät {} fehlgeschlagen", recipient.deviceId)
        }
    }

    /** `<public ntfy URL>/up…` becomes `<internal ntfy URL>/up…`; null when the endpoint is not a topic of our ntfy. */
    private fun internalUrl(endpoint: String): String? {
        val prefix = config.ntfyPublicUrl + "/"
        if (!endpoint.startsWith(prefix)) return null
        val rest = endpoint.removePrefix(prefix)
        if (!TOPIC.matches(rest)) return null
        return config.ntfyInternalUrl + "/" + rest
    }

    private companion object {
        /** UnifiedPush topics of ntfy are `up` plus random characters, optionally with the marker `?up=1`. */
        val TOPIC = Regex("up[A-Za-z0-9_-]{4,64}(\\?up=1)?")
        const val MAX_PAYLOAD_BYTES = 1500
    }
}
