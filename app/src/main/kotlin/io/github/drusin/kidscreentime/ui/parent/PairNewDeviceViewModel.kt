package io.github.drusin.kidscreentime.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.api.CreatePairingCodeRequest
import io.github.drusin.kidscreentime.api.CreatePairingCodeResponse
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PairingQr
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import io.github.drusin.kidscreentime.parent.CreateCodeResult
import io.github.drusin.kidscreentime.parent.ParentRepository
import io.github.drusin.kidscreentime.parent.SnapshotResult
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreateCodeError { REJECTED, UNAVAILABLE }

/** A Kopplungscode on screen. [remaining] counts down to [Duration.ZERO], the code is then expired. */
data class ShownCode(
    val code: String,
    /** The text of the QR code: Server-URL and Kopplungscode. */
    val qrContent: String,
    val role: DeviceRole,
    val childName: String?,
    val replacesDeviceName: String?,
    val remaining: Duration,
) {
    val expired: Boolean get() = remaining.isZero
}

data class PairNewDeviceUiState(
    /** The Eltern-Snapshot is loading: the Kind and its Kindgerät decide what the screen asks. */
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val child: ParentChildSnapshot? = null,
    /** Name of the Kindgerät a new one would replace, null when the Kind has none (Ersetzen-Hinweis). */
    val replacesDeviceName: String? = null,
    /** The chosen role. A Elterngerät code is created right away, for a Kindgerät the details come first. */
    val role: DeviceRole? = null,
    val newChildName: String = "",
    val creating: Boolean = false,
    val error: CreateCodeError? = null,
    val code: ShownCode? = null,
) {
    /** For a Kindgerät: there is no Kind yet, so the name comes first. */
    val needsChildName: Boolean get() = role == DeviceRole.CHILD && child == null
    val canCreateChildCode: Boolean
        get() = role == DeviceRole.CHILD && !creating && !loading && !unavailable && (!needsChildName || newChildName.isNotBlank())
}

/**
 * "Gerät koppeln" at the Elterngerät: choose the role (Kindgerät for the Kind, with the Ersetzen-Hinweis, or
 * Elterngerät), enter the Kind's name when there is none yet, then show the Kopplungscode as QR code and as text
 * with a countdown. The countdown runs on the monotonic clock, started from the server's validity.
 */
class PairNewDeviceViewModel(
    private val repository: ParentRepository,
    private val monotonicClock: MonotonicClock,
) : ViewModel() {
    private val _state = MutableStateFlow(PairNewDeviceUiState())
    val state: StateFlow<PairNewDeviceUiState> = _state.asStateFlow()

    private var expiresAt: Duration? = null

    init {
        viewModelScope.launch {
            when (val result = repository.loadSnapshot()) {
                is SnapshotResult.Loaded -> applySnapshot(result.snapshot)
                else -> _state.update { it.copy(loading = false, unavailable = true) }
            }
        }
    }

    private fun applySnapshot(snapshot: ParentSnapshot) {
        val child = snapshot.children.firstOrNull()
        val replaced = child?.let { kid -> snapshot.devices.firstOrNull { it.role == DeviceRole.CHILD && it.childId == kid.childId }?.name }
        _state.update { it.copy(loading = false, unavailable = false, child = child, replacesDeviceName = replaced) }
    }

    fun onRoleSelected(role: DeviceRole) {
        _state.update { it.copy(role = role, error = null) }
        if (role == DeviceRole.PARENT) createCode()
    }

    fun onNewChildNameChange(value: String) = _state.update { it.copy(newChildName = value, error = null) }

    fun createCode() {
        val current = _state.value
        val role = current.role ?: return
        if (current.creating || (role == DeviceRole.CHILD && !current.canCreateChildCode)) return
        val request = when (role) {
            DeviceRole.PARENT -> CreatePairingCodeRequest(DeviceRole.PARENT)
            DeviceRole.CHILD -> current.child?.let { CreatePairingCodeRequest(DeviceRole.CHILD, childId = it.childId) }
                ?: CreatePairingCodeRequest(DeviceRole.CHILD, newChildName = current.newChildName.trim())
        }
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createPairingCode(request)) {
                is CreateCodeResult.Created -> show(result.code)
                CreateCodeResult.Rejected -> _state.update { it.copy(creating = false, error = CreateCodeError.REJECTED) }
                CreateCodeResult.Unauthorized, CreateCodeResult.Unavailable ->
                    _state.update { it.copy(creating = false, error = CreateCodeError.UNAVAILABLE) }
            }
        }
    }

    private fun show(created: CreatePairingCodeResponse) {
        val validFor = Duration.ofMillis(created.expiresAtEpochMillis - created.serverTimeEpochMillis).coerceAtLeast(Duration.ZERO)
        expiresAt = monotonicClock.elapsed() + validFor
        _state.update {
            it.copy(
                creating = false,
                code = ShownCode(
                    code = created.code,
                    qrContent = PairingQr.encode(created.serverUrl, created.code),
                    role = created.role,
                    childName = created.childName,
                    replacesDeviceName = created.replacesDeviceName,
                    remaining = validFor,
                ),
            )
        }
        // The new code may have created nothing yet, but the Eltern-Snapshot in the response is the newest state.
        applySnapshot(created.snapshot)
    }

    /** Leaves the code view for a fresh one (after expiry, or to pair another device). */
    fun newCode() {
        expiresAt = null
        _state.update { it.copy(code = null, error = null, creating = false) }
        if (_state.value.role == DeviceRole.PARENT) createCode()
    }

    /** Back to the role choice. */
    fun backToRoleChoice() {
        expiresAt = null
        _state.update { it.copy(role = null, code = null, error = null, creating = false) }
    }

    /** Updates the countdown. The screen calls it every second (see `EverySecond`). */
    fun tick() {
        val end = expiresAt ?: return
        val remaining = (end - monotonicClock.elapsed()).coerceAtLeast(Duration.ZERO)
        _state.update { state -> state.copy(code = state.code?.copy(remaining = remaining)) }
    }
}
