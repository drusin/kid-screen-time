package io.github.drusin.kidscreentime.domain.clock

import java.time.Instant

/**
 * Wall clock: the current point in time as a calendar instant. It can jump (user changes the time, NTP).
 * Use it for dates, days and timestamps that are stored or shown, never to measure durations.
 *
 * Production code must obtain time only through this interface and [MonotonicClock]. Calling the system
 * clock directly is forbidden and checked by the architecture test.
 */
fun interface WallClock {
    fun now(): Instant
}
