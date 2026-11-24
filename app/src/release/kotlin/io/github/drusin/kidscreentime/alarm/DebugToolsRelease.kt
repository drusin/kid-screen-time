package io.github.drusin.kidscreentime.alarm

import android.content.Context
import androidx.compose.runtime.Composable
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.domain.clock.WallClock

/** Release has no test Sitzung and no debug tools (the debug build has the real ones in `src/debug`). */
fun createTestSession(context: Context, wallClock: WallClock): TestSession = NoTestSession

@Composable
fun DebugTools(container: AppContainer) = Unit
