package io.github.drusin.kidscreentime.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.api.DeviceInfo
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.api.ParentSnapshot
import io.github.drusin.kidscreentime.parent.ParentActionResult
import io.github.drusin.kidscreentime.parent.ParentRepository
import io.github.drusin.kidscreentime.parent.SnapshotResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentHomeUiState(
    val loading: Boolean = true,
    val children: List<ParentChildSnapshot> = emptyList(),
    /** The Geräteliste of the Eltern-Snapshot (Einstellungen & Geräte). */
    val devices: List<DeviceInfo> = emptyList(),
    /** The id of this device in [devices]. */
    val ownDeviceId: String? = null,
    /** Familien-Zeitzone (IANA id) from the snapshot, for times on the Status-Karte. */
    val timeZone: String? = null,
    /**
     * The server cannot be reached (ticket 17): the screens show the last known state from the cache, with the bar
     * "Offline · nur Ansicht", and every Eltern-Aktion is off (see [canAct]). Cleared by the next successful load.
     */
    val offline: Boolean = false,
    /** "Heute anpassen": the prepared Tagesanpassung in minutes (Stepper, multiple of 5), 0 = nothing prepared. */
    val pendingAdjustment: Int = 0,
    /** An Eltern-Aktion is on its way to the server. */
    val acting: Boolean = false,
    /** The last Eltern-Aktion failed (refused or no network). The prepared value stays so the parent can try again. */
    val actionFailed: Boolean = false,
) {
    /** The Kind the Eltern-Aktionen of the Startseite are about (v1: exactly one). */
    val child: ParentChildSnapshot? get() = children.firstOrNull()

    /**
     * Vorschau: the Tageskontingent after booking [pendingAdjustment], never below 0. Null without Kind or status.
     * Calculated from Vorlage plus the sum of today's Tagesanpassungen (not from the already clamped total).
     */
    val previewAllowanceMinutes: Int?
        get() = child?.status?.allowance?.let { (it.templateMinutes + it.adjustmentMinutes + pendingAdjustment).coerceAtLeast(0) }

    /**
     * The one switch of all Eltern-Aktionen (Buchen, Zurücknehmen, Wochenvorlage, Regeln, Koppeln, Entkoppeln): none is
     * possible while another is on its way or while the Elterngerät is [offline]. Screens enable their controls with this.
     */
    val canAct: Boolean get() = !acting && !offline

    val canBook: Boolean get() = child != null && pendingAdjustment != 0 && canAct

    /**
     * "Stand bekannt seit": the last contact of the Kindgerät with the server (its "zuletzt gesehen" in the Geräteliste),
     * i.e. how old the shown state of the Kindgerät can be. Null when the Kind has no Kindgerät or it never reported.
     */
    val knownSinceEpochMillis: Long?
        get() = child?.let { kid -> devices.firstOrNull { it.role == DeviceRole.CHILD && it.childId == kid.childId }?.lastSeenEpochMillis }

    /** The delta "Letzte zurücknehmen" would take back, null when there is none (the button is hidden). */
    val undoableAdjustmentMinutes: Int? get() = child?.lastAdjustmentMinutes

    /** Only the last Elterngerät cannot be entkoppelt (the server refuses it, the button is disabled). */
    fun canUnpair(device: DeviceInfo): Boolean = canAct && (device.role != DeviceRole.PARENT || devices.count { it.role == DeviceRole.PARENT } > 1)

    /** Hinweis "noch kein Kind gekoppelt". */
    val showNoChildHint: Boolean get() = children.isEmpty()
}

/** The Eltern-Startseite. Variante B ("Ein Bildschirm") grows here in later tickets. */
class ParentHomeViewModel(
    private val repository: ParentRepository,
    /** Fires when a push signal arrived (Resync-Signal): the Eltern-Snapshot is loaded again. */
    resyncs: Flow<*> = emptyFlow<Unit>(),
) : ViewModel() {
    private val _state = MutableStateFlow(ParentHomeUiState())
    val state: StateFlow<ParentHomeUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { resyncs.collect { refresh() } }
    }

    /** Stepper: moves the prepared Tagesanpassung by [minutes] (±[ADJUSTMENT_STEP]), within ±[MAX_ADJUSTMENT]. */
    fun stepAdjustment(minutes: Int) {
        _state.update { it.copy(pendingAdjustment = (it.pendingAdjustment + minutes).coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT), actionFailed = false) }
    }

    /** Books the prepared Tagesanpassung. */
    fun bookAdjustment() {
        val current = _state.value
        val child = current.child ?: return
        if (!current.canBook) return
        runAction { repository.bookAdjustment(child.childId, current.pendingAdjustment) }
    }

    /** Takes back the last Tagesanpassung of today (Gegenbuchung at the server). */
    fun undoLastAdjustment() {
        val current = _state.value
        val child = current.child ?: return
        if (!current.canAct || current.undoableAdjustmentMinutes == null) return
        runAction { repository.undoLastAdjustment(child.childId) }
    }

    /**
     * Saves the Wochenvorlage ([minutes] Mo–So); it is valid from tomorrow. [onDone] runs when the server accepted it
     * (the dialog closes then). On failure the dialog stays open with its input.
     */
    fun saveWeeklyTemplate(minutes: List<Int>, onDone: () -> Unit = {}) {
        val current = _state.value
        val child = current.child ?: return
        if (!current.canAct || minutes.size != 7 || minutes.any { it !in 0..MAX_ADJUSTMENT }) return
        runAction(onDone) { repository.setWeeklyTemplate(child.childId, minutes) }
    }

    /** Saves name, Vorwarnschwelle and Überziehungsgrenze of the Kind. [onDone] as in [saveWeeklyTemplate]. */
    fun saveRules(name: String, warningThresholdMinutes: Int, overrunLimitMinutes: Int, onDone: () -> Unit = {}) {
        val current = _state.value
        val child = current.child ?: return
        if (!current.canAct || name.isBlank() || warningThresholdMinutes !in 0..MAX_ADJUSTMENT || overrunLimitMinutes !in 0..MAX_ADJUSTMENT) return
        runAction(onDone) { repository.setRules(child.childId, name.trim(), warningThresholdMinutes, overrunLimitMinutes) }
    }

    /** Unpairs the Gerät [deviceId] of the Geräteliste (also this device, never the last Elterngerät). */
    fun unpairDevice(deviceId: String) {
        val current = _state.value
        val device = current.devices.firstOrNull { it.id == deviceId } ?: return
        if (!current.canUnpair(device)) return
        runAction { repository.unpairDevice(deviceId) }
    }

    /** Clears the failure message (a dialog was opened or its input changed). */
    fun clearActionFailed() {
        _state.update { it.copy(actionFailed = false) }
    }

    /**
     * The one pattern of every Eltern-Aktion (tickets 09, 10, 17): the repository call returns the new Eltern-Snapshot,
     * which becomes the screen state (that is the Abgleich, no extra load). When the server refused, the Snapshot is
     * loaded again, because then the screen was out of date (for example a second Elterngerät booked meanwhile).
     */
    private fun runAction(onDone: () -> Unit = {}, call: suspend () -> ParentActionResult) {
        if (!_state.value.canAct) return
        _state.update { it.copy(acting = true, actionFailed = false) }
        viewModelScope.launch {
            when (val result = call()) {
                is ParentActionResult.Done -> {
                    _state.update { applySnapshot(it, result.snapshot).copy(pendingAdjustment = 0, acting = false) }
                    onDone()
                }
                ParentActionResult.Rejected -> {
                    _state.update { it.copy(acting = false, actionFailed = true) }
                    refresh()
                }
                ParentActionResult.Unauthorized -> _state.update { it.copy(acting = false, actionFailed = true) }
                // No network: from now on the screens are read-only until a load succeeds again.
                ParentActionResult.Unavailable -> _state.update { it.copy(acting = false, actionFailed = true, offline = true) }
            }
        }
    }

    private fun applySnapshot(state: ParentHomeUiState, snapshot: ParentSnapshot) =
        state.copy(loading = false, offline = false, children = snapshot.children, timeZone = snapshot.timeZone, devices = snapshot.devices, ownDeviceId = repository.ownDeviceId)

    /** Called when the app comes back to the foreground: an offline Elterngerät tries the server again (the Abgleich at app start is [refresh] in `init`). */
    fun refreshIfOffline() {
        if (_state.value.offline) refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = repository.loadSnapshot()
            _state.update {
                when (result) {
                    is SnapshotResult.Loaded -> applySnapshot(it, result.snapshot)
                    is SnapshotResult.Cached -> applySnapshot(it, result.snapshot).copy(offline = true)
                    SnapshotResult.Unavailable -> it.copy(loading = false, offline = true)
                    SnapshotResult.Unauthorized -> it.copy(loading = false)
                }
            }
        }
    }

    companion object {
        const val ADJUSTMENT_STEP = 5

        /** Step of the Wochenvorlage and the rules. */
        const val SETTINGS_STEP = 5
        const val MAX_ADJUSTMENT = 24 * 60
    }
}
