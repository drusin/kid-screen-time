package io.github.drusin.kidscreentime.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.pairing.PairResult
import io.github.drusin.kidscreentime.pairing.PairingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PairError { INVALID_SERVER_URL, INVALID_CODE, RATE_LIMITED, SERVER_UNREACHABLE, UNEXPECTED }

data class PairDeviceUiState(
    val serverUrl: String = "",
    val code: String = "",
    val deviceName: String = "",
    val submitting: Boolean = false,
    val error: PairError? = null,
) {
    val canSubmit: Boolean get() = !submitting && serverUrl.isNotBlank() && code.isNotBlank() && deviceName.isNotBlank()
}

/** "Gerät koppeln": Server-URL, Kopplungscode and device name. On success the root screen switches by itself. */
class PairDeviceViewModel(
    private val repository: PairingRepository,
    defaultDeviceName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PairDeviceUiState(deviceName = defaultDeviceName))
    val state: StateFlow<PairDeviceUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onCodeChange(value: String) = _state.update { it.copy(code = value, error = null) }
    fun onDeviceNameChange(value: String) = _state.update { it.copy(deviceName = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = repository.pair(current.serverUrl, current.code, current.deviceName)
            _state.update {
                it.copy(
                    submitting = false,
                    error = when (result) {
                        PairResult.Success -> null
                        PairResult.InvalidServerUrl -> PairError.INVALID_SERVER_URL
                        PairResult.InvalidCode -> PairError.INVALID_CODE
                        PairResult.RateLimited -> PairError.RATE_LIMITED
                        PairResult.ServerUnreachable -> PairError.SERVER_UNREACHABLE
                        PairResult.UnexpectedError -> PairError.UNEXPECTED
                    },
                )
            }
        }
    }
}
