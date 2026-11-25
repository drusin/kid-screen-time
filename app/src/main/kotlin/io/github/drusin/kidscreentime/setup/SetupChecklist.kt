package io.github.drusin.kidscreentime.setup

import android.content.Context
import android.content.SharedPreferences
import io.github.drusin.kidscreentime.api.SetupItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A Punkt the system cannot tell us about: the Eltern tick it off once they did it. It never goes to the server and never shows the banner. */
enum class SetupHint { NEVER_SLEEPING_APPS, DO_NOT_DISTURB_ALARMS }

/** [missing]: prüfbare Punkte that are missing as of the last [SetupChecklist.refresh]. [checkedHints]: the hints that were ticked off. */
data class SetupChecklistState(
    val missing: List<SetupItem> = emptyList(),
    val checkedHints: Set<SetupHint> = emptySet(),
)

/**
 * The Einrichtungs-Checkliste of the Kindgerät: the last result of the [SetupChecks] and the ticked hints (kept in
 * SharedPreferences). [refresh] checks again; `ChildRepository.sync` calls it for every sync, so app start, unlock, push and
 * the retry job all check, and the screens read [state].
 */
class SetupChecklist(private val checks: SetupChecks, private val preferences: SharedPreferences) {
    constructor(checks: SetupChecks, context: Context) : this(checks, context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    private val _state = MutableStateFlow(SetupChecklistState(checkedHints = storedHints()))
    val state: StateFlow<SetupChecklistState> = _state.asStateFlow()

    /** Checks the system now and returns the missing prüfbare Punkte. */
    fun refresh(): List<SetupItem> {
        val missing = checks.missing()
        _state.update { it.copy(missing = missing) }
        return missing
    }

    fun setHintChecked(hint: SetupHint, checked: Boolean) {
        val hints = _state.value.checkedHints.let { if (checked) it + hint else it - hint }
        preferences.edit().putStringSet(KEY_HINTS, hints.map { it.name }.toSet()).apply()
        _state.update { it.copy(checkedHints = hints) }
    }

    private fun storedHints(): Set<SetupHint> =
        preferences.getStringSet(KEY_HINTS, emptySet()).orEmpty().mapNotNull { name -> SetupHint.entries.firstOrNull { it.name == name } }.toSet()

    private companion object {
        const val PREFERENCES = "setup-checklist"
        const val KEY_HINTS = "checked-hints"
    }
}
