package io.github.drusin.kidscreentime.server

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** A Sitzung the server ended by itself because the Überziehungsgrenze was reached. [endedAt] is the end set on the Sitzung. */
data class AutomaticStop(
    val childId: String,
    val sessionId: String,
    val endedAt: Instant,
    val childName: String,
    /** The Überziehungsgrenze in force when the Sitzung was stopped. */
    val overrunLimit: Duration,
)

fun interface AutomaticStopListener {
    fun automaticallyStopped(stop: AutomaticStop)
}

/**
 * The Automatischer Stopp at the server: the [TimedEventSource] that ends a running Sitzung at the time `domain` names
 * (`Ausloeser.AUTOMATISCHER_STOPP`), also when the Kindgerät is offline.
 *
 * "A Sitzung was stopped automatically" is the one point other features hook into: [onStopped] listeners run after the
 * end is committed (ticket 13: Resync-Signal to the Kindgerät; ticket 14: notification to the Eltern, text built in [ServerServices.create]).
 */
class AutomaticStopEvents(private val children: ChildService) : TimedEventSource {
    private val listeners = CopyOnWriteArrayList<AutomaticStopListener>()

    fun onStopped(listener: AutomaticStopListener) {
        listeners += listener
    }

    override fun nextDue(childId: String, now: Instant): Instant? = children.nextAutomaticStop(childId, now)

    override fun runDue(childId: String, now: Instant) {
        val stop = children.stopAutomatically(childId, now) ?: return
        // One failing listener must not keep the others from running (the end is committed already).
        listeners.forEach { runCatching { it.automaticallyStopped(stop) } }
    }
}
