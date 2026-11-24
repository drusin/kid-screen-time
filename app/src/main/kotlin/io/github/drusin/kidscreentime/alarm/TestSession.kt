package io.github.drusin.kidscreentime.alarm

import io.github.drusin.kidscreentime.domain.bildschirmzeit.KindStand
import java.time.Instant

/**
 * A local Sitzung that replaces the real stand for trying out the alarm at the device. Only the debug build has one
 * (`createTestSession` exists once per build type, in `src/debug` and `src/release`); release gets [NoTestSession].
 */
interface TestSession {
    /** A test Sitzung is running (it replaces the real stand). */
    val active: Boolean

    /** The stand of a running test Sitzung, null when there is none. */
    fun stand(now: Instant): KindStand?

    /** Ends the test Sitzung (the "Stopp" of the alarm screen). */
    fun stop()
}

object NoTestSession : TestSession {
    override val active: Boolean = false
    override fun stand(now: Instant): KindStand? = null
    override fun stop() = Unit
}
