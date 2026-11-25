package io.github.drusin.kidscreentime.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.api.HistoryDay
import io.github.drusin.kidscreentime.parent.HistoryResult
import io.github.drusin.kidscreentime.parent.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    /** Newest day first, including today. Empty until loaded. */
    val days: List<HistoryDay> = emptyList(),
    /**
     * The server cannot be reached. The full Verlauf is an Eltern-Aktion that needs the server and is not cached, so the
     * screen shows a hint instead of data (the Startseite keeps its "letzte Tage" from the cached Eltern-Snapshot).
     */
    val unavailable: Boolean = false,
    /** The server does not know the Kind (the family changed since the Startseite was loaded). */
    val rejected: Boolean = false,
)

/** The full Verlauf of one Kind: loaded when the screen opens and on "Erneut versuchen". */
class HistoryViewModel(private val repository: ParentRepository, private val childId: String) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, unavailable = false, rejected = false) }
        viewModelScope.launch {
            val result = repository.history(childId)
            _state.update {
                when (result) {
                    is HistoryResult.Loaded -> HistoryUiState(loading = false, days = result.history.days)
                    HistoryResult.Rejected -> it.copy(loading = false, rejected = true)
                    HistoryResult.Unavailable -> it.copy(loading = false, unavailable = true)
                    HistoryResult.Unauthorized -> it.copy(loading = false)
                }
            }
        }
    }
}
