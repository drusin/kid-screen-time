package io.github.drusin.kidscreentime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand

/** Zustandsfarben: bereit = Primärfarbe, läuft = Grün, Vorwarnung = Bernstein, Überziehung = Rot, aufgebraucht = Grau. */
@Composable
fun stateColor(zustand: Zustand): Color {
    val dark = isSystemInDarkTheme()
    return when (zustand) {
        Zustand.BEREIT -> MaterialTheme.colorScheme.primary
        Zustand.LAEUFT -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        Zustand.VORWARNUNG -> if (dark) Color(0xFFFFB74D) else Color(0xFFB26A00)
        Zustand.UEBERZIEHUNG -> if (dark) Color(0xFFEF5350) else Color(0xFFB3261D)
        Zustand.AUFGEBRAUCHT -> if (dark) Color(0xFFBDBDBD) else Color(0xFF757575)
    }
}

@Composable
fun stateColor(state: ChildState): Color = stateColor(
    when (state) {
        ChildState.READY -> Zustand.BEREIT
        ChildState.RUNNING -> Zustand.LAEUFT
        ChildState.WARNING -> Zustand.VORWARNUNG
        ChildState.OVERRUN -> Zustand.UEBERZIEHUNG
        ChildState.EXHAUSTED -> Zustand.AUFGEBRAUCHT
    },
)
