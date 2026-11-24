package io.github.drusin.kidscreentime.clock

import java.time.Duration
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class AndroidMonotonicClockTest {
    @Test
    fun `Monotone App-Uhr folgt der vorgestellten Systemzeit`() {
        val before = AndroidMonotonicClock.elapsed()

        ShadowSystemClock.advanceBy(Duration.ofSeconds(90))

        assertEquals(Duration.ofSeconds(90), AndroidMonotonicClock.elapsed() - before)
    }
}
