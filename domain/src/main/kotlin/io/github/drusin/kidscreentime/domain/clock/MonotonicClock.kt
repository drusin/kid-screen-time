package io.github.drusin.kidscreentime.domain.clock

import java.time.Duration

/**
 * Monotonic clock: elapsed time since an arbitrary, fixed origin. It never jumps backwards and ignores
 * changes of the wall clock. Only differences between two readings are meaningful. Use it to measure
 * how long something ran (for example a Sitzung).
 */
fun interface MonotonicClock {
    fun elapsed(): Duration
}
