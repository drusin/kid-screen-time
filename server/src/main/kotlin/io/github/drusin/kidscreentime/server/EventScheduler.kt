package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.domain.clock.WallClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * One kind of timed event of the server (Automatischer Stopp today; midnight in ticket 11 and the "Restzeit 0"
 * notification in ticket 14 are added as further sources). A source holds no schedule of its own: both functions
 * work from the database state, so the scheduler can always start from scratch (after a change, after a restart).
 * The rules of when something is due belong to `domain` (`KindStand.faelligeZeitpunkte`), not to the source.
 */
interface TimedEventSource {
    /** The earliest time this source has something to do for [childId], null if nothing is pending. A time not after [now] is due. */
    fun nextDue(childId: String, now: Instant): Instant?

    /** Carries out what is due for [childId] at [now]. Must be idempotent and must leave nothing due that it handled. */
    fun runDue(childId: String, now: Instant)
}

/**
 * Plans the timed events in the process: no job queue, no stored schedule. For every Kind it asks every
 * [TimedEventSource] for the next due time and sleeps until the earliest of them. It calculates from the database again
 * after each change ([wake], called when the stand of a Kind changes), after each event, and at start (a restart picks
 * up everything that became due while the server was down). The clock is injected: tests move a `ManualClock` and call
 * [runDue] instead of waiting.
 */
class EventScheduler(
    private val wallClock: WallClock,
    private val children: ChildService,
    private val sources: List<TimedEventSource>,
) {
    private val log = LoggerFactory.getLogger(EventScheduler::class.java)
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** The stand of a Kind changed: calculate the next due time again. */
    fun wake() {
        wakeUp.trySend(Unit)
    }

    /** The earliest due time of all Kinder and sources, null when nothing is pending. */
    fun nextDue(): Instant? {
        val now = wallClock.now()
        return children.childIds().flatMap { id -> sources.mapNotNull { it.nextDue(id, now) } }.minOrNull()
    }

    /** Carries out everything that is due at the current time. Returns how many (child, source) pairs ran. */
    fun runDue(): Int {
        val now = wallClock.now()
        var ran = 0
        for (id in children.childIds()) {
            for (source in sources) {
                val due = source.nextDue(id, now)
                if (due != null && !due.isAfter(now)) {
                    source.runDue(id, now)
                    ran++
                }
            }
        }
        return ran
    }

    /** The loop of the running server: run what is due, sleep until the next time or until woken. Ends only by cancellation. */
    suspend fun run() {
        while (true) {
            var failed = false
            try {
                runDue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                log.error("Zeitgesteuertes Ereignis fehlgeschlagen, neuer Versuch folgt", e)
            }
            val wait = try {
                nextDue()?.let { Duration.between(wallClock.now(), it) } ?: MAX_SLEEP
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Nächster Zeitpunkt nicht berechenbar", e)
                failed = true
                MAX_SLEEP
            }
            // Something still due right after running means it failed: do not spin. The cap also covers jumps of the wall clock.
            val sleep = if (failed || wait <= Duration.ZERO) RETRY_AFTER else minOf(wait, MAX_SLEEP)
            withTimeoutOrNull(sleep.toMillis()) { wakeUp.receive() }
        }
    }

    companion object {
        val MAX_SLEEP: Duration = Duration.ofMinutes(1)
        val RETRY_AFTER: Duration = Duration.ofSeconds(1)
    }
}
