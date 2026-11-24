package io.github.drusin.kidscreentime.domain.clock

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManualClockTest {
    private val start = Instant.parse("2026-03-29T00:30:00Z")

    @Test
    fun `Zeit vorstellen bewegt Wanduhr und monotone Uhr gemeinsam`() {
        val clock = ManualClock(start = start, startElapsed = Duration.ofSeconds(10))

        clock.advance(Duration.ofMinutes(45))

        assertEquals(start.plus(Duration.ofMinutes(45)), clock.now())
        assertEquals(Duration.ofSeconds(10).plusMinutes(45), clock.elapsed())
    }

    @Test
    fun `Wanduhr umstellen laesst die monotone Uhr unberuehrt`() {
        val clock = ManualClock(start = start)

        clock.setWallClock(start.minus(Duration.ofHours(3)))

        assertEquals(start.minus(Duration.ofHours(3)), clock.now())
        assertEquals(Duration.ZERO, clock.elapsed())
    }

    @Test
    fun `Zeit laeuft nie rueckwaerts`() {
        val clock = ManualClock(start = start)

        assertFailsWith<IllegalArgumentException> { clock.advance(Duration.ofSeconds(-1)) }
    }

    @Test
    fun `Uhren sind ueber die Schnittstellen nutzbar`() {
        val clock = ManualClock(start = start)
        val wall: WallClock = clock
        val monotonic: MonotonicClock = clock

        clock.advance(Duration.ofSeconds(5))

        assertEquals(start.plusSeconds(5), wall.now())
        assertEquals(Duration.ofSeconds(5), monotonic.elapsed())
    }
}
