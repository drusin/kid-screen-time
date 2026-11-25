package io.github.drusin.kidscreentime.server

import java.time.Instant

/**
 * Midnight at the server (ticket 11): the [TimedEventSource] that makes the scheduler wake exactly when a new day
 * starts for a Kind whose Sitzung runs past midnight (`Ausloeser.MITTERNACHT`, the time `domain` names in the Familien-Zeitzone,
 * also on days of 23 or 25 hours).
 *
 * There is nothing to carry out: all values are calculated from the database for the time asked, so at midnight the
 * new Tageskontingent applies by itself. What matters is that the scheduler calculates again then, and so judges the Kinder
 * again: [AutomaticStopEvents] now sees the new day (its next time moves, a stop due by the old day is gone), and sources
 * added later (ticket 14, notification "Restzeit 0") see the new Restzeit. Because the due time always lies in the future of
 * the time asked, [runDue] is never called by the scheduler and has no work.
 */
class MidnightEvents(private val children: ChildService) : TimedEventSource {
    override fun nextDue(childId: String, now: Instant): Instant? = children.nextMidnight(childId, now)

    override fun runDue(childId: String, now: Instant) = Unit
}
