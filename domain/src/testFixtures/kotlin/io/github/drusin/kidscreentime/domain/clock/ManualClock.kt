package io.github.drusin.kidscreentime.domain.clock

import java.time.Duration
import java.time.Instant

/**
 * Controllable clock for tests: implements [WallClock] and [MonotonicClock]. Time only moves when the
 * test says so. Time is advanced, never waited for.
 */
class ManualClock(
    start: Instant = Instant.parse("2026-01-05T08:00:00Z"),
    startElapsed: Duration = Duration.ZERO,
) : WallClock, MonotonicClock {
    private var wall: Instant = start
    private var monotonic: Duration = startElapsed

    override fun now(): Instant = wall

    override fun elapsed(): Duration = monotonic

    /** Real time passes: both clocks move forward by [duration]. */
    fun advance(duration: Duration) {
        require(!duration.isNegative) { "Time cannot run backwards: $duration" }
        wall += duration
        monotonic += duration
    }

    /**
     * The device restarts: the wall clock keeps running during the [downtime] and afterwards, the monotonic clock
     * starts again at zero (like elapsed realtime after a boot), so every monotonic value taken before is invalid.
     */
    fun reboot(downtime: Duration) {
        require(!downtime.isNegative) { "Time cannot run backwards: $downtime" }
        wall += downtime
        monotonic = Duration.ZERO
    }

    /** Someone changes the wall clock (user, NTP). The monotonic clock does not move. */
    fun setWallClock(instant: Instant) {
        wall = instant
    }
}
