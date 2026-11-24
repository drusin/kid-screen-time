package io.github.drusin.kidscreentime.alarm

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
 * Later tickets add due times in `domain` (Automatischer Stopp, Mitternacht) and handle the new [Ausloeser] in
 * [trigger]; planning needs no change.
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

    /** The alarm fired: does what is due and plans the next time. Returns what was due. */
    suspend fun trigger(): List<Ausloeser> = lock.withLock {
        val local = repository.local.first()
        val now = wallClock.now()
        val kind = kindStand(local)
        val sessionId = kind?.tagesstand(now)?.laufendeSitzung?.id
        if (kind == null || sessionId == null) {
            planLocked(local)
            return emptyList()
        }
        var verlauf = memory.load(sessionId)
        // A late alarm may find both due: the ring covers the Vorwarnung.
        var due = kind.faelligeZeitpunkte(now, verlauf).faelligBis(now).map { it.ausloeser }
        if (Ausloeser.ALARM in due) due = due - Ausloeser.VORWARNUNG
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
            }
        }
        memory.save(sessionId, verlauf)
        planLocked(local)
        due
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
