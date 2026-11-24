package io.github.drusin.kidscreentime.clock

import android.os.SystemClock
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import java.time.Duration

/**
 * Monotonic clock for the app, based on elapsed realtime. Unlike `System.nanoTime` it keeps counting
 * during deep sleep, which matters for Sitzungen on a tablet with the screen off.
 * Allowed to call the system clock (allow-list in the architecture test).
 */
object AndroidMonotonicClock : MonotonicClock {
    override fun elapsed(): Duration = Duration.ofNanos(SystemClock.elapsedRealtimeNanos())
}
