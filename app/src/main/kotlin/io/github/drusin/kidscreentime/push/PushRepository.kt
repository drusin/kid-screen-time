package io.github.drusin.kidscreentime.push

import android.content.Context
import android.content.SharedPreferences
import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.PushRegistration
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remembers the push registration the distributor gave us and the one the server already knows, in private
 * SharedPreferences. The two differ until [PushRepository.submit] succeeded (e.g. no network, not paired yet).
 */
class PushStorage(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("push", Context.MODE_PRIVATE))

    fun current(): PushRegistration? = read("current")

    fun submitted(): PushRegistration? = read("submitted")

    fun saveCurrent(registration: PushRegistration) = write("current", registration)

    fun saveSubmitted(registration: PushRegistration) = write("submitted", registration)

    /** After the distributor dropped us or this device was entkoppelt: nothing is registered any more. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun read(prefix: String): PushRegistration? = PushRegistration(
        endpoint = prefs.getString("$prefix.endpoint", null) ?: return null,
        publicKey = prefs.getString("$prefix.publicKey", null) ?: return null,
        authSecret = prefs.getString("$prefix.authSecret", null) ?: return null,
    )

    private fun write(prefix: String, registration: PushRegistration) {
        prefs.edit()
            .putString("$prefix.endpoint", registration.endpoint)
            .putString("$prefix.publicKey", registration.publicKey)
            .putString("$prefix.authSecret", registration.authSecret)
            .apply()
    }
}

/**
 * Keeps the server's idea of this device's push endpoint and WebPush keys current. The distributor reports a new
 * endpoint ([onNewEndpoint]) whenever it changes; [submit] tells the server, once paired. There is no retry job: a
 * failed call is repeated at the next app start and the next endpoint event (the server tolerates a stale endpoint,
 * it only misses signals until then, and every sync still heals the state).
 */
class PushRepository(
    private val httpClient: HttpClient,
    private val pairing: PairingRepository,
    private val storage: PushStorage,
) {
    private val lock = Mutex()

    /** The distributor delivered an endpoint with the public keys for this app instance. */
    suspend fun onNewEndpoint(registration: PushRegistration) {
        storage.saveCurrent(registration)
        submit()
    }

    /** The distributor unregistered this app; nothing to tell the server (it drops dead topics itself). */
    fun onUnregistered() = storage.clear()

    /** Sends the current registration to the server when it is paired and the server does not know it yet. */
    suspend fun submit(): Boolean = lock.withLock {
        val current = storage.current() ?: return false
        if (current == storage.submitted()) return true
        val device = (pairing.state.value as? DeviceState.Paired)?.device ?: return false
        val token = pairing.token() ?: return false
        try {
            val response = httpClient.post(device.serverUrl + ApiPaths.PUSH_REGISTRATION) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(current)
            }
            if (response.status == HttpStatusCode.NoContent) {
                storage.saveSubmitted(current)
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
