package io.github.drusin.kidscreentime.server

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

fun interface RestzeitNullListener {
    fun reached(event: RestzeitNull)
}

/**
 * The notification "Restzeit 0" (ticket 14) as a [TimedEventSource]: due when the Restzeit of a running Sitzung reaches 0
 * (the Alarm time of `domain`), also when the Kindgerät is offline. Listeners run after the "gemeldet" mark is committed.
 *
 * Exactly once per Sitzung and day, also across restarts: the one memory is the mark (session, day) in `session_notifications`, set in the
 * transaction that decides ([ChildService.reportRestzeitNull]). Everything else is derived from the database:
 * - A running Sitzung the server learns of late (sync, restart) that is already in the Überziehung is due at once: one
 *   notification now.
 * - A Sitzung that was ended before the server ever saw it running (offline) is not running, so nothing is due: no
 *   catch-up notification. The same holds for one the Automatischer Stopp ended first (that source runs first).
 * - A Tagesanpassung that lifts the Restzeit above 0 again after the notification does not re-arm it: one per Sitzung and day.
 * - A Sitzung running past midnight gets its own notification when the Restzeit of the new day reaches 0.
 * - A crash between the mark and the push loses the notification rather than repeating it ("nie doppelt").
 */
class RestzeitNullEvents(private val children: ChildService) : TimedEventSource {
    private val listeners = CopyOnWriteArrayList<RestzeitNullListener>()

    fun onReached(listener: RestzeitNullListener) {
        listeners += listener
    }

    override fun nextDue(childId: String, now: Instant): Instant? = children.nextRestzeitNull(childId, now)

    override fun runDue(childId: String, now: Instant) {
        val event = children.reportRestzeitNull(childId, now) ?: return
        listeners.forEach { runCatching { it.reached(event) } }
    }
}
