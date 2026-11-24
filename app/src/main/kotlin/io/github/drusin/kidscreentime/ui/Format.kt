package io.github.drusin.kidscreentime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import java.time.Duration

/** `mm:ss` (minutes are not wrapped at 60: 75 minutes are `75:00`). */
fun Duration.toClockText(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Whole minutes, rounded up so that 30 s left never reads "0 min". */
fun Duration.toMinutesRoundedUp(): Long = (seconds.coerceAtLeast(0) + 59) / 60

/** "45 min" or "1 h 15 min". */
@Composable
fun minutesText(minutes: Long): String =
    if (minutes < 60) stringResource(R.string.duration_minutes, minutes)
    else stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)

@Composable
fun minutesText(minutes: Int): String = minutesText(minutes.toLong())

/** "+25 min" / "−15 min" / "±0 min". */
@Composable
fun signedMinutesText(minutes: Int): String = when {
    minutes > 0 -> "+" + minutesText(minutes)
    minutes < 0 -> "−" + minutesText(-minutes)
    else -> "±" + minutesText(0)
}

@Composable
fun stateWord(zustand: Zustand): String = stringResource(
    when (zustand) {
        Zustand.BEREIT -> R.string.state_ready
        Zustand.LAEUFT -> R.string.state_running
        Zustand.VORWARNUNG -> R.string.state_warning
        Zustand.UEBERZIEHUNG -> R.string.state_overrun
        Zustand.AUFGEBRAUCHT -> R.string.state_exhausted
    },
)

@Composable
fun stateWord(state: ChildState): String = stringResource(
    when (state) {
        ChildState.READY -> R.string.state_ready
        ChildState.RUNNING -> R.string.state_running
        ChildState.WARNING -> R.string.state_warning
        ChildState.OVERRUN -> R.string.state_overrun
        ChildState.EXHAUSTED -> R.string.state_exhausted
    },
)
