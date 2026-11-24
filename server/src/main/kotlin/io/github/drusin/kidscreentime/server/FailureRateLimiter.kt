package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import java.time.Duration

/**
 * Global (not per IP) limit for failed attempts at redeeming a Kopplungscode or entering the Admin-Passwort.
 * After [maxFailures] failures within [window] every attempt is blocked until the oldest failure ages out.
 */
class FailureRateLimiter(
    private val clock: MonotonicClock,
    private val maxFailures: Int = 10,
    private val window: Duration = Duration.ofMinutes(1),
) {
    private val failures = ArrayDeque<Duration>()

    @Synchronized
    fun isBlocked(): Boolean {
        val now = clock.elapsed()
        while (failures.isNotEmpty() && now - failures.first() >= window) failures.removeFirst()
        return failures.size >= maxFailures
    }

    @Synchronized
    fun recordFailure() {
        failures.addLast(clock.elapsed())
    }
}
