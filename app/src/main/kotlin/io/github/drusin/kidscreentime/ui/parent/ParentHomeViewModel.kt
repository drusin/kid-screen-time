package io.github.drusin.kidscreentime.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.parent.ParentRepository
import io.github.drusin.kidscreentime.parent.SnapshotResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentHomeUiState(
    val loading: Boolean = true,
    val children: List<ParentChildSnapshot> = emptyList(),
    /** Familien-Zeitzone (IANA id) from the snapshot, for times on the Status-Karte. */
    val timeZone: String? = null,
    /** The Eltern-Snapshot could not be loaded. The ticket 17 cache will show the last known state instead. */
    val unavailable: Boolean = false,
) {
    /** Hinweis "noch kein Kind gekoppelt". */
    val showNoChildHint: Boolean get() = children.isEmpty()
}

/** The Eltern-Startseite. Variante B ("Ein Bildschirm") grows here in later tickets. */
class ParentHomeViewModel(private val repository: ParentRepository) : ViewModel() {
    private val _state = MutableStateFlow(ParentHomeUiState())
    val state: StateFlow<ParentHomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = repository.loadSnapshot()
            _state.update {
                when (result) {
                    is SnapshotResult.Loaded -> ParentHomeUiState(loading = false, children = result.snapshot.children, timeZone = result.snapshot.timeZone)
                    SnapshotResult.Unauthorized, SnapshotResult.Unavailable -> it.copy(loading = false, unavailable = true)
                }
            }
        }
    }
}
