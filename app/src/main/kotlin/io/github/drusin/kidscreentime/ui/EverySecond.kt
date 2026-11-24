package io.github.drusin.kidscreentime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Calls [onTick] every second while the screen is visible. The clock of a screen belongs to the UI, not to the
 * ViewModel: the ViewModel only offers a `tick()`, so tests advance time by calling it and never wait.
 */
@Composable
fun EverySecond(onTick: () -> Unit) {
    val current by rememberUpdatedState(onTick)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                current()
                delay(1_000)
            }
        }
    }
}
