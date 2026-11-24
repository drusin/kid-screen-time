package io.github.drusin.kidscreentime.pairing

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.RedeemPairingCodeRequest
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the app is: not paired yet, or paired with a fixed role. The root screen follows this state. */
sealed interface DeviceState {
    data object Unpaired : DeviceState
    data class Paired(val device: PairedDevice) : DeviceState
}

sealed interface PairResult {
    data object Success : PairResult
    data object InvalidServerUrl : PairResult
    data object InvalidCode : PairResult
    data object RateLimited : PairResult

    /** `409`: the Kind already has a Kindgerät and replacing it is not possible yet (ticket 12). */
    data object ChildHasDevice : PairResult
    data object ServerUnreachable : PairResult
    data object UnexpectedError : PairResult
}

/**
 * Kopplung of this device: redeems a Kopplungscode, keeps the Geräte-Token in the [TokenStore] and the rest in
 * [PairingStorage]. The role never changes afterwards; there is no way back to [DeviceState.Unpaired] except
 * [unpair] (called when the server answers `401`, ticket 12).
 */
class PairingRepository(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore,
    private val storage: PairingStorage,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    /** The Geräte-Token for Bearer headers, null when not paired. */
    fun token(): String? = tokenStore.load()

    suspend fun pair(serverUrlInput: String, code: String, deviceName: String): PairResult {
        val serverUrl = normalizeServerUrl(serverUrlInput) ?: return PairResult.InvalidServerUrl
        val response = try {
            httpClient.post(serverUrl + ApiPaths.PAIRING_REDEEM) {
                contentType(ContentType.Application.Json)
                setBody(RedeemPairingCodeRequest(code = code.trim(), deviceName = deviceName.trim()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return PairResult.ServerUnreachable
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = try {
                    response.body<RedeemPairingCodeResponse>()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return PairResult.UnexpectedError
                }
                tokenStore.save(body.deviceToken)
                val device = PairedDevice(serverUrl, body.deviceId, deviceName.trim(), body.role, body.childId, body.childName)
                storage.save(device)
                _state.value = DeviceState.Paired(device)
                PairResult.Success
            }
            HttpStatusCode.BadRequest -> PairResult.InvalidCode
            HttpStatusCode.TooManyRequests -> PairResult.RateLimited
            HttpStatusCode.Conflict -> PairResult.ChildHasDevice
            else -> PairResult.UnexpectedError
        }
    }

    /** Forgets the Kopplung locally (token and data). */
    fun unpair() {
        tokenStore.clear()
        storage.clear()
        _state.value = DeviceState.Unpaired
    }

    private fun initialState(): DeviceState {
        val device = storage.load()
        // Without the token the stored data is useless; start over at "Gerät koppeln".
        return if (device != null && tokenStore.load() != null) DeviceState.Paired(device) else DeviceState.Unpaired
    }

    companion object {
        /** `kst.example.org` becomes `https://kst.example.org`. Returns null when it is not a usable URL. */
        fun normalizeServerUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = try {
                Url(withScheme)
            } catch (_: Exception) {
                return null
            }
            if (url.protocol != URLProtocol.HTTPS && url.protocol != URLProtocol.HTTP) return null
            if (url.host.isBlank()) return null
            return withScheme
        }
    }
}
