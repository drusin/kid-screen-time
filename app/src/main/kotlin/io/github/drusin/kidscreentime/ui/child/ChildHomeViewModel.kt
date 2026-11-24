package io.github.drusin.kidscreentime.ui.child

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.child.ChildRefreshResult
import io.github.drusin.kidscreentime.child.ChildRepository
import io.github.drusin.kidscreentime.child.StoredChildSnapshot
import io.github.drusin.kidscreentime.child.toKindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import io.github.drusin.kidscreentime.domain.clock.WallClock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildHomeUiState(
    val loading: Boolean = true,
    val childName: String? = null,
    /** Calculated by `domain` from the stored snapshot, null until the first sync succeeded. */
    val stand: Tagesstand? = null,
    /** Server time of the snapshot (in the Familien-Zeitzone) the values are calculated with ("gerechnet mit dem Stand von …"). */
    val snapshotAt: ZonedDateTime? = null,
    /** The last sync failed: the values come from the stored snapshot. */
    val offline: Boolean = false,
)

/**
 * The Kindgerät home screen (Variante A "Ring"). It shows no settings. It reads the snapshot from Room and
 * calculates the Tagesstand locally with `domain`; [tick] repeats the calculation as time passes (also across midnight).
 */
class ChildHomeViewModel(
    private val repository: ChildRepository,
    private val wallClock: WallClock,
) : ViewModel() {
    private val _state = MutableStateFlow(ChildHomeUiState())
    val state: StateFlow<ChildHomeUiState> = _state.asStateFlow()

    private var stored: StoredChildSnapshot? = null

    init {
        viewModelScope.launch {
            repository.stored.collect {
                stored = it
                recalculate()
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = repository.refresh()
            _state.update { it.copy(loading = false, offline = result != ChildRefreshResult.Refreshed) }
            recalculate()
        }
    }

    /** Calculates again for the current time. The screen calls it every second (see `EverySecond`). */
    fun tick() = recalculate()

    private fun recalculate() {
        val current = stored
        _state.update {
            if (current == null) {
                it.copy(stand = null, childName = null, snapshotAt = null)
            } else {
                it.copy(
                    childName = current.snapshot.childName,
                    stand = current.snapshot.toKindStand().tagesstand(wallClock.now()),
                    snapshotAt = Instant.ofEpochMilli(current.snapshot.serverTimeEpochMillis).atZone(ZoneId.of(current.snapshot.timeZone)),
                    loading = false,
                )
            }
        }
    }
}
