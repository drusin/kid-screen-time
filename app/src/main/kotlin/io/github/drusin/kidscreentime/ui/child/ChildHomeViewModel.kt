package io.github.drusin.kidscreentime.ui.child

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.api.SetupItem
import io.github.drusin.kidscreentime.child.ChildLocalState
import io.github.drusin.kidscreentime.child.ChildRepository
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.child.toKindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.github.drusin.kidscreentime.setup.SetupChecklist
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the big button does right now. */
enum class ChildAction {
    /** Restzeit > 0, nothing running: "Start". */
    START,

    /** A Sitzung runs (also in the Überziehung): "Stopp". */
    STOP,

    /** Restzeit 0 and nothing running: the button is disabled, "Keine Zeit mehr übrig". */
    NO_TIME_LEFT,

    /** No stand yet (the first sync did not succeed): no button. */
    UNAVAILABLE,
}

data class ChildHomeUiState(
    val loading: Boolean = true,
    val childName: String? = null,
    /** Calculated by `domain` from the stored snapshot and the Outbox, null until the first sync succeeded. */
    val stand: Tagesstand? = null,
    /** Server time of the snapshot (in the Familien-Zeitzone) the values are calculated with ("gerechnet mit dem Stand von …"). */
    val snapshotAt: ZonedDateTime? = null,
    /** The last sync failed: the values come from the stored snapshot and the Outbox. */
    val offline: Boolean = false,
    val action: ChildAction = ChildAction.UNAVAILABLE,
    /** The prüfbaren Punkte of the Einrichtungs-Checkliste that are missing (as of the last check): the red banner shows when not empty. */
    val setupMissing: List<SetupItem> = emptyList(),
)

/**
 * The Kindgerät home screen (Variante A "Ring"). It shows no settings. It reads snapshot and Outbox from Room and
 * calculates the Tagesstand locally with `domain`, so Start and Stopp show at once without waiting for the server;
 * [tick] repeats the calculation as time passes (also across midnight). It syncs at the start of the screen and after
 * every Start or Stopp ([onAction]).
 */
class ChildHomeViewModel(
    private val repository: ChildRepository,
    private val wallClock: WallClock,
    private val setupChecklist: SetupChecklist,
) : ViewModel() {
    private val _state = MutableStateFlow(ChildHomeUiState())
    val state: StateFlow<ChildHomeUiState> = _state.asStateFlow()

    private var local: ChildLocalState? = null

    /** Serializes taps: each decision sees the effect of the previous one. */
    private val actionLock = Mutex()

    init {
        viewModelScope.launch {
            repository.local.collect {
                local = it
                recalculate()
            }
        }
        viewModelScope.launch {
            setupChecklist.state.collect { checklist -> _state.update { it.copy(setupMissing = checklist.missing) } }
        }
        // The first sync of the screen (every sync checks the setup, so this is the check "at app start").
        sync()
    }

    private var resumedBefore = false

    /**
     * The screen came back to the foreground (also from the system settings the Checkliste links to): sync, which checks the setup
     * again, so a fixed Punkt goes away and the server learns of it. The first call is the start of the screen and already synced.
     */
    fun onResume() {
        if (resumedBefore) sync() else resumedBefore = true
    }

    fun sync() {
        viewModelScope.launch { syncNow() }
    }

    private suspend fun syncNow() {
        val result = repository.sync()
        _state.update { it.copy(loading = false, offline = result != ChildSyncResult.Synced) }
        recalculate()
    }

    /**
     * The big button: Start when the Restzeit allows it, Stopp while a Sitzung runs, nothing at Restzeit 0. The change
     * goes to the Outbox first (the UI shows it from there), then the sync follows at once. "No Start at Restzeit ≤ 0"
     * is checked only here, with the stand of this device; the server never rejects a Start.
     */
    fun onAction() {
        viewModelScope.launch {
            val acted = actionLock.withLock {
                val current = repository.local.first() ?: return@withLock false
                val stand = current.stand(wallClock)
                when (actionFor(stand)) {
                    ChildAction.START -> repository.startSession()
                    ChildAction.STOP -> repository.stopSession(checkNotNull(stand.laufendeSitzung).id)
                    ChildAction.NO_TIME_LEFT, ChildAction.UNAVAILABLE -> return@withLock false
                }
                true
            }
            if (acted) syncNow()
        }
    }

    /** Calculates again for the current time. The screen calls it every second (see `EverySecond`). */
    fun tick() = recalculate()

    private fun recalculate() {
        val current = local
        _state.update {
            if (current == null) {
                it.copy(stand = null, childName = null, snapshotAt = null, action = ChildAction.UNAVAILABLE)
            } else {
                val stand = current.stand(wallClock)
                val snapshot = current.stored.snapshot
                it.copy(
                    childName = snapshot.childName,
                    stand = stand,
                    snapshotAt = Instant.ofEpochMilli(snapshot.serverTimeEpochMillis).atZone(ZoneId.of(snapshot.timeZone)),
                    action = actionFor(stand),
                    loading = false,
                )
            }
        }
    }
}

private fun ChildLocalState.stand(wallClock: WallClock): Tagesstand =
    toKindStand().tagesstand(wallClock.now())

private fun actionFor(stand: Tagesstand): ChildAction = when {
    stand.laufendeSitzung != null -> ChildAction.STOP
    stand.zustand == Zustand.AUFGEBRAUCHT -> ChildAction.NO_TIME_LEFT
    else -> ChildAction.START
}
