package io.github.drusin.kidscreentime.domain.clock

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemClocksTest {
    @Test
    fun `System-Wanduhr liefert eine plausible aktuelle Zeit`() {
        assertTrue(SystemWallClock.now().isAfter(java.time.Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `Systemnahe monotone Uhr geht nie rueckwaerts`() {
        val first = SystemMonotonicClock.elapsed()
        val second = SystemMonotonicClock.elapsed()
        assertFalse(second < first)
    }
}
