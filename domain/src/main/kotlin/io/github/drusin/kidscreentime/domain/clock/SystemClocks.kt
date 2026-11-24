package io.github.drusin.kidscreentime.domain.clock

import java.time.Duration
import java.time.Instant

/**
 * The real clocks for JVM code (server, tests that need real time). This file is one of the few places
 * allowed to call the system clock (see the allow-list in the architecture test).
 * The Android app uses its own monotonic clock based on elapsed realtime, because `System.nanoTime`
 * stops during deep sleep.
 */
object SystemWallClock : WallClock {
    override fun now(): Instant = Instant.now()
}

object SystemMonotonicClock : MonotonicClock {
    override fun elapsed(): Duration = Duration.ofNanos(System.nanoTime())
}
