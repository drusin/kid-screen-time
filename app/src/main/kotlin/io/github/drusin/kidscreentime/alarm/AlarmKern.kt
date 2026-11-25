package io.github.drusin.kidscreentime.alarm

import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.child.ChildLocalState
import io.github.drusin.kidscreentime.child.ChildRepository
import io.github.drusin.kidscreentime.child.toKindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Alarmverlauf
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Ausloeser
import io.github.drusin.kidscreentime.domain.bildschirmzeit.KindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.faelligeZeitpunkte
import io.github.drusin.kidscreentime.domain.clock.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Alarmkern of the Kindgerät. It owns no rules: `domain` says what is due when (`faelligeZeitpunkte`), the
 * kern only turns that into one `setAlarmClock` for the earliest time and into effects when the alarm fires.
 *
 * - [replan] cancels and plans again from the local stand (snapshot plus Outbox). It runs after every change of that
 *   stand, see [observe]: Start, Stopp, every sync (also one a push triggered), and at app start after a reboot.
 * - [trigger] runs when the alarm fires: it carries out everything that is due (`when` over [Ausloeser]), remembers it,
 *   and plans the next time.
 *
 * Automatischer Stopp (ticket 07): the Sitzung is stopped through the Outbox at the time `domain` names, and synced.
 * Mitternacht (ticket 11) is an [Ausloeser] that only plans again. Later tickets add due times in `domain` and handle the
 * new [Ausloeser] in [trigger]; planning needs no change.
 */
class AlarmKern(
    private val repository: ChildRepository,
    private val scheduler: AlarmScheduler,
    private val memory: AlarmMemory,
    private val effects: AlarmEffects,
    private val wallClock: WallClock,
    private val testSession: TestSession = NoTestSession,
) {
    private val lock = Mutex()

    /** Plans again whenever the local stand changes. This is the one hook: everything that changes the stand is covered. */
    fun observe(scope: CoroutineScope) {
        scope.launch { repository.local.collect { replan(it) } }
    }

    /** Plans again from the current local stand. */
    suspend fun replan() = replan(repository.local.first())

    suspend fun replan(local: ChildLocalState?) = lock.withLock { planLocked(local) }

    /** The stand the alarm works with right now (a test Sitzung of the debug build wins), null before the first sync. */
    suspend fun currentStand(): Tagesstand? = kindStand(repository.local.first())?.tagesstand(wallClock.now())

    /**
     * The alarm fired: does what is due and plans the next time. Returns what was due. After an Automatischer Stopp the
     * Outbox is synced at once (outside the lock: it is network).
     */
    suspend fun trigger(): List<Ausloeser> {
        val due = lock.withLock { triggerLocked() }
        if (Ausloeser.AUTOMATISCHER_STOPP in due) repository.sync()
        return due
    }

    private suspend fun triggerLocked(): List<Ausloeser> {
        val local = repository.local.first()
        val now = wallClock.now()
        val kind = kindStand(local)
        val sessionId = kind?.tagesstand(now)?.laufendeSitzung?.id
        if (kind == null || sessionId == null) {
            planLocked(local)
            return emptyList()
        }
        var verlauf = memory.load(sessionId)
        val faellig = kind.faelligeZeitpunkte(now, verlauf).faelligBis(now)
        var due = faellig.map { it.ausloeser }
        // A late alarm may find both due: the ring covers the Vorwarnung. After the Stopp nothing rings any more.
        if (Ausloeser.ALARM in due) due = due - Ausloeser.VORWARNUNG
        if (Ausloeser.AUTOMATISCHER_STOPP in due) due = listOf(Ausloeser.AUTOMATISCHER_STOPP)
        due.forEach { ausloeser ->
            when (ausloeser) {
                Ausloeser.VORWARNUNG -> {
                    verlauf = verlauf.copy(vorwarnungGegeben = true)
                    effects.warn()
                }
                Ausloeser.ALARM -> {
                    verlauf = verlauf.copy(vorwarnungGegeben = true, letzterAlarm = now)
                    effects.ring()
                }
                // Nothing to carry out: at midnight a new Tageskontingent starts. The plan below calculates again from the
                // new day (a ringing Alarm ends if the Restzeit is above 0 again, the next times come from `domain`).
                Ausloeser.MITTERNACHT -> Unit
                Ausloeser.AUTOMATISCHER_STOPP -> {
                    // The end is the time the limit was reached, like at the server (the earlier end wins, so no conflict).
                    val zeitpunkt = faellig.first { it.ausloeser == Ausloeser.AUTOMATISCHER_STOPP }.zeitpunkt
                    if (testSession.active) testSession.stop() else repository.stopSession(sessionId, zeitpunkt, SessionEndReason.AUTOMATIC_STOP)
                }
            }
        }
        memory.save(sessionId, verlauf)
        // The stand changed through the Outbox: plan from the new one (the Sitzung is over, so nothing is planned and the ring ends).
        planLocked(if (Ausloeser.AUTOMATISCHER_STOPP in due) repository.local.first() else local)
        return due
    }

    private fun planLocked(local: ChildLocalState?) {
        val now = wallClock.now()
        val kind = kindStand(local)
        val stand = kind?.tagesstand(now)
        val sessionId = stand?.laufendeSitzung?.id
        if (kind == null || sessionId == null) {
            scheduler.cancel()
            memory.clear()
            effects.silence()
            return
        }
        // A ringing Alarm ends as soon as the Restzeit is above 0 again (for example after a Tagesanpassung).
        if (stand.restzeit.isPositive) effects.silence()
        val next = kind.faelligeZeitpunkte(now, memory.load(sessionId)).naechster
        if (next == null) scheduler.cancel() else scheduler.schedule(maxOf(next.zeitpunkt, now))
    }

    private fun kindStand(local: ChildLocalState?): KindStand? =
        testSession.stand(wallClock.now()) ?: local?.stored?.snapshot?.toKindStand(local.pending)
}
