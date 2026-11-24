package io.github.drusin.kidscreentime.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drusin.kidscreentime.child.ChildRepository
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlarmUiState(
    val loaded: Boolean = false,
    val stand: Tagesstand? = null,
) {
    /** Nothing to ring about any more: no Sitzung runs or the Restzeit is above 0 again. The screen closes. */
    val finished: Boolean get() = loaded && (stand?.laufendeSitzung == null || stand.restzeit.isPositive)
}

/**
 * The full-screen Alarm: "Zeit ist um!", the Überziehung, "Stopp" and "Ton aus". "Stopp" ends the Sitzung through the
 * Outbox like the button of the home screen; "Ton aus" only ends the current ringing, the Alarmkern keeps the next
 * repetition planned.
 */
class AlarmViewModel(
    private val kern: AlarmKern,
    private val repository: ChildRepository,
    private val testSession: TestSession,
    private val silence: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AlarmUiState())
    val state: StateFlow<AlarmUiState> = _state.asStateFlow()

    init {
        tick()
    }

    /** Calculates again for the current time; the screen calls it every second (see `EverySecond`). */
    fun tick() {
        viewModelScope.launch { _state.value = AlarmUiState(loaded = true, stand = kern.currentStand()) }
    }

    fun stop() {
        viewModelScope.launch {
            val running = kern.currentStand()?.laufendeSitzung ?: return@launch
            if (testSession.active) {
                testSession.stop()
            } else {
                repository.stopSession(running.id)
            }
            silence()
            // The Alarmkern cancels its alarm when the local stand changes; a test Sitzung has no Outbox, so plan now.
            kern.replan()
            tick()
            repository.sync()
        }
    }

    /** "Ton aus": only this ringing ends. */
    fun silenceOnce() = silence()
}
