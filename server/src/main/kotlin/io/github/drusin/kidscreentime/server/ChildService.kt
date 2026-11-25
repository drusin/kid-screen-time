package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.AllowanceBreakdown
import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.api.ChildStatus
import io.github.drusin.kidscreentime.api.DayAdjustment
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.api.RunningSession
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionRecord
import io.github.drusin.kidscreentime.api.WeeklyTemplateVersion
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Endegrund
import io.github.drusin.kidscreentime.domain.bildschirmzeit.KindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Sitzung
import io.github.drusin.kidscreentime.domain.bildschirmzeit.SitzungsEnde
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesanpassung
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Wochenvorlage
import io.github.drusin.kidscreentime.domain.bildschirmzeit.WochenvorlageVersion
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.faelligeZeitpunkte
import io.github.drusin.kidscreentime.domain.clock.WallClock
import java.sql.Connection
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Kinder: creating them (with the Standardwerte) and reading their state. The values come from `domain`
 * ([KindStand.tagesstand]); this class only loads the input from the database and maps the result to the DTOs.
 */
class ChildService(
    private val database: Database,
    private val wallClock: WallClock,
    private val zone: ZoneId,
) {
    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()

    /** [listener] runs after every committed change of the stand of a Kind (the event scheduler calculates again then). */
    fun onStandChanged(listener: () -> Unit) {
        changeListeners += listener
    }

    fun notifyStandChanged() = changeListeners.forEach { it() }

    /**
     * Creates a Kind with the Standardwerte of `domain`: Wochenvorlage valid from today (so the Kind has time
     * right away), Vorwarnschwelle and Überziehungsgrenze. Runs in the caller's transaction.
     */
    fun create(c: Connection, name: String, now: Instant): String {
        val id = UUID.randomUUID().toString()
        c.prepareStatement(
            "INSERT INTO children (id, name, created_at, warning_threshold_minutes, overrun_limit_minutes) VALUES (?,?,?,?,?)",
        ).use {
            it.setString(1, id)
            it.setString(2, name)
            it.setLong(3, now.toEpochMilli())
            it.setLong(4, KindStand.STANDARD_VORWARNSCHWELLE.toMinutes())
            it.setLong(5, KindStand.STANDARD_UEBERZIEHUNGSGRENZE.toMinutes())
            it.executeUpdate()
        }
        val standard = WochenvorlageVersion.standard(now.atZone(zone).toLocalDate())
        c.prepareStatement(
            "INSERT INTO weekly_template_versions (child_id, valid_from, mon, tue, wed, thu, fri, sat, sun) VALUES (?,?,?,?,?,?,?,?,?)",
        ).use {
            it.setString(1, id)
            it.setString(2, standard.gueltigAb.toString())
            DayOfWeek.entries.forEachIndexed { index, day -> it.setInt(3 + index, standard.minuten.getValue(day)) }
            it.executeUpdate()
        }
        return id
    }

    fun childIds(): List<String> = database.transaction { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT id FROM children ORDER BY created_at, id").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    /** When the running Sitzung of [childId] reaches the Überziehungsgrenze (may lie in the past), null if none runs. */
    fun nextAutomaticStop(childId: String, now: Instant): Instant? = database.transaction { c ->
        load(c, childId, now)?.stand?.faelligeZeitpunkte(now)?.automatischerStopp
    }

    /**
     * Ends the running Sitzung of [childId] when its Automatischer Stopp is due at [now]: the end is the time the
     * Überziehungsgrenze was reached (not [now]), the reason "Automatischer Stopp". Null when nothing was due.
     */
    fun stopAutomatically(childId: String, now: Instant): AutomaticStop? = database.transaction { c ->
        val stand = load(c, childId, now)?.stand ?: return@transaction null
        val stop = stand.faelligeZeitpunkte(now).automatischerStopp?.takeUnless { it.isAfter(now) } ?: return@transaction null
        val session = stand.tagesstand(now).laufendeSitzung ?: return@transaction null
        val changed = c.prepareStatement(
            "UPDATE sessions SET end_at = MAX(?, start_at), end_reason = 'AUTOMATIC_STOP' WHERE id = ? AND end_at IS NULL",
        ).use {
            it.setLong(1, stop.toEpochMilli())
            it.setString(2, session.id)
            it.executeUpdate()
        }
        if (changed == 1) AutomaticStop(childId, session.id, stop) else null
    }

    fun exists(childId: String): Boolean = database.transaction { c ->
        c.prepareStatement("SELECT 1 FROM children WHERE id = ?").use { st ->
            st.setString(1, childId)
            st.executeQuery().use { it.next() }
        }
    }

    fun count(): Int = database.transaction { c ->
        c.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM children").use { it.next(); it.getInt(1) } }
    }

    /** Name of the active Kindgerät of [childId], null when the Kind has none. */
    fun activeChildDeviceName(childId: String): String? = database.transaction { c -> activeChildDeviceName(c, childId) }

    fun activeChildDeviceName(c: Connection, childId: String): String? =
        c.prepareStatement("SELECT name FROM devices WHERE child_id = ? AND role = 'CHILD' AND unpaired_at IS NULL").use { st ->
            st.setString(1, childId)
            st.executeQuery().use { if (it.next()) it.getString(1) else null }
        }

    /**
     * The sync of a Kindgerät: upserts the Sitzungen of its Outbox and returns the complete Kind-Snapshot, all in
     * one transaction. A Sitzung is a fact: nothing is rejected because of the Restzeit (the Kindgerät checks that).
     * Rules per [SessionUpdate]:
     * - Times are "now − age" with now = server time at receipt, never in the future.
     * - An unknown id creates the Sitzung. For a known one the start stays (a repeated batch changes nothing).
     * - The earlier end wins (in time): a later stop for an already ended Sitzung (e.g. after an Automatischer Stopp)
     *   changes nothing, an earlier one replaces the end and the reason becomes "gestoppt".
     * - A new Sitzung starts no earlier than the end of the previous one (overlaps are cut) and is marked "offline
     *   erfasst" when the server learns of its start later than [OFFLINE_DELAY] after it.
     */
    fun sync(childId: String, updates: List<SessionUpdate>): SyncResult {
        val invalid = updates.firstOrNull { runCatching { UUID.fromString(it.id) }.isFailure }
        if (invalid != null) return SyncResult.Invalid("Ungültige Sitzungs-ID: ${invalid.id}")
        val now = wallClock.now()
        val result = try {
            database.transaction { c ->
                var changed = false
                for (update in updates.sortedBy { -it.startAgeMillis }) {
                    when (upsert(c, childId, update, now)) {
                        Upsert.FOREIGN -> throw ForeignSessionException()
                        Upsert.CHANGED -> changed = true
                        Upsert.UNCHANGED -> Unit
                    }
                }
                val snapshot = checkNotNull(snapshot(c, childId, now)) { "Das Kind des Kindgeräts fehlt" }
                SyncResult.Synced(snapshot, changed)
            }
        } catch (_: ForeignSessionException) {
            // Thrown inside the transaction on purpose: everything of this batch is rolled back.
            SyncResult.ForeignSession
        }
        if (result is SyncResult.Synced) notifyStandChanged()
        return result
    }

    /**
     * Books a Tagesanpassung of [deltaMinutes] for today (append only). Afterwards the stand counts as changed
     * (scheduler recalculates). The caller sends the Resync-Signal.
     */
    fun bookAdjustment(childId: String, deltaMinutes: Int, deviceId: String): AdjustmentResult {
        val now = wallClock.now()
        val result = database.transaction { c ->
            if (!childExists(c, childId)) return@transaction AdjustmentResult.UnknownChild
            insertAdjustment(c, childId, now, deltaMinutes, deviceId, reversesId = null)
            AdjustmentResult.Booked
        }
        if (result == AdjustmentResult.Booked) notifyStandChanged()
        return result
    }

    /**
     * Takes back the last Tagesanpassung of today that is not yet taken back, as a Gegenbuchung (the same amount with the
     * opposite sign, pointing at the original).
     */
    fun undoLastAdjustment(childId: String, deviceId: String): AdjustmentResult {
        val now = wallClock.now()
        val result = database.transaction { c ->
            if (!childExists(c, childId)) return@transaction AdjustmentResult.UnknownChild
            val last = lastUndoableAdjustment(c, childId, now.atZone(zone).toLocalDate()) ?: return@transaction AdjustmentResult.NothingToTakeBack
            insertAdjustment(c, childId, now, -last.second, deviceId, reversesId = last.first)
            AdjustmentResult.Booked
        }
        if (result == AdjustmentResult.Booked) notifyStandChanged()
        return result
    }

    /**
     * Sets the Wochenvorlage as a new version valid from tomorrow (a second change today replaces that version; today's
     * Tageskontingent stays). No midnight job: the Wochenvorlage picks the version by date.
     */
    fun setWeeklyTemplate(childId: String, minutes: List<Int>): ChangeResult {
        val now = wallClock.now()
        val validFrom = now.atZone(zone).toLocalDate().plusDays(1)
        val result = database.transaction { c ->
            if (!childExists(c, childId)) return@transaction ChangeResult.UnknownChild
            c.prepareStatement(
                "INSERT OR REPLACE INTO weekly_template_versions (child_id, valid_from, mon, tue, wed, thu, fri, sat, sun) VALUES (?,?,?,?,?,?,?,?,?)",
            ).use {
                it.setString(1, childId)
                it.setString(2, validFrom.toString())
                minutes.forEachIndexed { index, value -> it.setInt(3 + index, value) }
                it.executeUpdate()
            }
            ChangeResult.Changed
        }
        if (result == ChangeResult.Changed) notifyStandChanged()
        return result
    }

    /** Sets name, Vorwarnschwelle and Überziehungsgrenze of the Kind (they apply right away). */
    fun setRules(childId: String, name: String, warningMinutes: Int, overrunMinutes: Int): ChangeResult {
        val result = database.transaction { c ->
            val changed = c.prepareStatement(
                "UPDATE children SET name = ?, warning_threshold_minutes = ?, overrun_limit_minutes = ? WHERE id = ?",
            ).use {
                it.setString(1, name)
                it.setInt(2, warningMinutes)
                it.setInt(3, overrunMinutes)
                it.setString(4, childId)
                it.executeUpdate()
            }
            if (changed == 1) ChangeResult.Changed else ChangeResult.UnknownChild
        }
        if (result == ChangeResult.Changed) notifyStandChanged()
        return result
    }

    enum class ChangeResult { Changed, UnknownChild }

    enum class AdjustmentResult { Booked, UnknownChild, NothingToTakeBack }

    private fun childExists(c: Connection, childId: String): Boolean =
        c.prepareStatement("SELECT 1 FROM children WHERE id = ?").use { st ->
            st.setString(1, childId)
            st.executeQuery().use { it.next() }
        }

    private fun insertAdjustment(c: Connection, childId: String, now: Instant, delta: Int, deviceId: String, reversesId: Long?) {
        c.prepareStatement(
            "INSERT INTO daily_adjustments (child_id, day, delta_minutes, booked_at, device_id, reverses_id) VALUES (?,?,?,?,?,?)",
        ).use {
            it.setString(1, childId)
            it.setString(2, now.atZone(zone).toLocalDate().toString())
            it.setInt(3, delta)
            it.setLong(4, now.toEpochMilli())
            it.setString(5, deviceId)
            if (reversesId == null) it.setNull(6, java.sql.Types.INTEGER) else it.setLong(6, reversesId)
            it.executeUpdate()
        }
    }

    /** (id, delta) of the latest Tagesanpassung of [tag] that is no Gegenbuchung and was not taken back yet. */
    private fun lastUndoableAdjustment(c: Connection, childId: String, tag: LocalDate): Pair<Long, Int>? =
        c.prepareStatement(
            "SELECT a.id, a.delta_minutes FROM daily_adjustments a WHERE a.child_id = ? AND a.day = ? AND a.reverses_id IS NULL " +
                "AND NOT EXISTS (SELECT 1 FROM daily_adjustments r WHERE r.reverses_id = a.id) ORDER BY a.id DESC LIMIT 1",
        ).use { st ->
            st.setString(1, childId)
            st.setString(2, tag.toString())
            st.executeQuery().use { if (it.next()) it.getLong(1) to it.getInt(2) else null }
        }

    private class ForeignSessionException : RuntimeException()

    private enum class Upsert { FOREIGN, CHANGED, UNCHANGED }

    /** [Upsert.FOREIGN] when the id belongs to a Sitzung of another Kind, [Upsert.CHANGED] when a Start or Stopp was new. */
    private fun upsert(c: Connection, childId: String, update: SessionUpdate, now: Instant): Upsert {
        var changed = false
        val existing = c.prepareStatement("SELECT child_id FROM sessions WHERE id = ?").use { st ->
            st.setString(1, update.id)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        if (existing == null) {
            // A Start learned late is "offline erfasst". Times lie in [end of the previous Sitzung, now].
            val startAge = update.startAgeMillis.coerceAtLeast(0)
            val start = maxOf(now.minusMillis(startAge).toEpochMilli(), previousEnd(c, childId, now.minusMillis(startAge)))
            c.prepareStatement("INSERT INTO sessions (id, child_id, start_at, recorded_offline) VALUES (?,?,?,?)").use {
                it.setString(1, update.id)
                it.setString(2, childId)
                it.setLong(3, start)
                it.setInt(4, if (startAge > OFFLINE_DELAY.toMillis()) 1 else 0)
                it.executeUpdate()
            }
            changed = true
        } else if (existing != childId) {
            return Upsert.FOREIGN
        }
        val endAge = update.endAgeMillis
        if (endAge != null) {
            // The earlier end wins (in time, not in arrival): an open Sitzung gets the end, an already ended one only
            // gets an end that lies clearly earlier (e.g. the child stopped offline before the server's Automatischer
            // Stopp), then the reason is "gestoppt". A later end changes nothing. Ends within END_TOLERANCE count as
            // equal, so a repeated batch (the age was measured again) cannot move the end. The end never lies before the start.
            c.prepareStatement(
                "UPDATE sessions SET end_at = MAX(?, start_at), end_reason = 'STOPPED' " +
                    "WHERE id = ? AND (end_at IS NULL OR MAX(?, start_at) < end_at - ?)",
            ).use {
                val end = now.minusMillis(endAge.coerceAtLeast(0)).toEpochMilli()
                it.setLong(1, end)
                it.setString(2, update.id)
                it.setLong(3, end)
                it.setLong(4, END_TOLERANCE.toMillis())
                if (it.executeUpdate() > 0) changed = true
            }
        }
        return if (changed) Upsert.CHANGED else Upsert.UNCHANGED
    }

    /** The end of the latest ended Sitzung of the Kind that started no later than [start] (epoch millis), 0 if none. */
    private fun previousEnd(c: Connection, childId: String, start: Instant): Long =
        c.prepareStatement("SELECT MAX(end_at) FROM sessions WHERE child_id = ? AND end_at IS NOT NULL AND start_at <= ?").use { st ->
            st.setString(1, childId)
            st.setLong(2, start.toEpochMilli())
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }

    /** The complete Kind-Snapshot for the Kindgerät of [childId]. */
    fun childSnapshot(childId: String): ChildSnapshot? {
        val now = wallClock.now()
        return database.transaction { c -> snapshot(c, childId, now) }
    }

    private fun snapshot(c: Connection, childId: String, now: Instant): ChildSnapshot? {
        val loaded = load(c, childId, now) ?: return null
        val tag = now.atZone(zone).toLocalDate()
        val stand = loaded.stand
        val offline = offlineSessionIds(c, childId)
        return ChildSnapshot(
            childId = childId,
            childName = loaded.name,
            serverTimeEpochMillis = now.toEpochMilli(),
            timeZone = zone.id,
            status = stand.tagesstand(now).toStatus(),
            warningThresholdMinutes = stand.vorwarnschwelle.toMinutes().toInt(),
            overrunLimitMinutes = stand.ueberziehungsgrenze.toMinutes().toInt(),
            templateToday = stand.wochenvorlage.dtoFor(tag),
            templateTomorrow = stand.wochenvorlage.dtoFor(tag.plusDays(1)),
            todaySessions = stand.sitzungen.map { it.toRecord(offline) },
            todayAdjustments = stand.tagesanpassungen.map { DayAdjustment(it.tag.toString(), it.deltaMinuten, it.gebuchtAm.toEpochMilli()) },
        )
    }

    /** The ids of the Sitzungen of [childId] marked "offline erfasst". */
    private fun offlineSessionIds(c: Connection, childId: String): Set<String> =
        c.prepareStatement("SELECT id FROM sessions WHERE child_id = ? AND recorded_offline = 1").use { st ->
            st.setString(1, childId)
            st.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
        }

    /** The Kinder for the Eltern-Snapshot (in v1 exactly one or none), oldest first. */
    fun parentChildren(): List<ParentChildSnapshot> {
        val now = wallClock.now()
        val tag = now.atZone(zone).toLocalDate()
        return database.transaction { c ->
            val ids = c.createStatement().use { st ->
                st.executeQuery("SELECT id FROM children ORDER BY created_at, id").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
            ids.mapNotNull { id -> load(c, id, now)?.let { id to it } }
        }.map { (id, loaded) ->
            val stand = loaded.stand
            ParentChildSnapshot(
                childId = id,
                name = loaded.name,
                status = stand.tagesstand(now).toStatus(),
                warningThresholdMinutes = stand.vorwarnschwelle.toMinutes().toInt(),
                overrunLimitMinutes = stand.ueberziehungsgrenze.toMinutes().toInt(),
                weeklyTemplate = stand.wochenvorlage.dtoFor(tag),
                weeklyTemplateTomorrow = stand.wochenvorlage.dtoFor(tag.plusDays(1)),
                lastAdjustmentMinutes = loaded.lastAdjustment,
            )
        }
    }

    private companion object {
        /** A Start the server learns of later than this after it happened is "offline erfasst" (a slow request is not). */
        val OFFLINE_DELAY: Duration = Duration.ofMinutes(1)

        /** Two ends this close count as the same end (ages are measured again with every sync). */
        val END_TOLERANCE: Duration = Duration.ofSeconds(1)
    }

    private class Loaded(val name: String, val stand: KindStand, val lastAdjustment: Int? = null)

    /** Loads the input of `domain` for the day of [now]: Wochenvorlage, today's Sitzungen and Tagesanpassungen, rules. */
    private fun load(c: Connection, childId: String, now: Instant): Loaded? {
        val tag = now.atZone(zone).toLocalDate()
        val tagesbeginn = tag.atStartOfDay(zone).toInstant()
        val (name, warning, overrun) = c.prepareStatement(
            "SELECT name, warning_threshold_minutes, overrun_limit_minutes FROM children WHERE id = ?",
        ).use { st ->
            st.setString(1, childId)
            st.executeQuery().use { rs -> if (rs.next()) Triple(rs.getString(1), rs.getLong(2), rs.getLong(3)) else null }
        } ?: return null

        val versions = c.prepareStatement(
            "SELECT valid_from, mon, tue, wed, thu, fri, sat, sun FROM weekly_template_versions WHERE child_id = ?",
        ).use { st ->
            st.setString(1, childId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            WochenvorlageVersion(
                                LocalDate.parse(rs.getString(1)),
                                DayOfWeek.entries.associateWith { rs.getInt(2 + it.ordinal) },
                            ),
                        )
                    }
                }
            }
        }
        val adjustments = c.prepareStatement(
            "SELECT day, delta_minutes, booked_at FROM daily_adjustments WHERE child_id = ? AND day = ? ORDER BY booked_at, id",
        ).use { st ->
            st.setString(1, childId)
            st.setString(2, tag.toString())
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Tagesanpassung(LocalDate.parse(rs.getString(1)), rs.getInt(2), Instant.ofEpochMilli(rs.getLong(3)))) }
            }
        }
        val sessions = c.prepareStatement(
            "SELECT id, start_at, end_at, end_reason FROM sessions WHERE child_id = ? AND (end_at IS NULL OR end_at > ?) ORDER BY start_at, id",
        ).use { st ->
            st.setString(1, childId)
            st.setLong(2, tagesbeginn.toEpochMilli())
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val end = rs.getLong(3).takeUnless { rs.wasNull() }
                        add(
                            Sitzung(
                                rs.getString(1),
                                Instant.ofEpochMilli(rs.getLong(2)),
                                end?.let { SitzungsEnde(Instant.ofEpochMilli(it), if (rs.getString(4) == "AUTOMATIC_STOP") Endegrund.AUTOMATISCHER_STOPP else Endegrund.GESTOPPT) },
                            ),
                        )
                    }
                }
            }
        }
        val stand = KindStand(
            zeitzone = zone,
            wochenvorlage = Wochenvorlage(versions),
            tagesanpassungen = adjustments,
            sitzungen = sessions,
            vorwarnschwelle = Duration.ofMinutes(warning),
            ueberziehungsgrenze = Duration.ofMinutes(overrun),
        )
        return Loaded(name, stand, lastUndoableAdjustment(c, childId, tag)?.second)
    }
}

private fun Tagesstand.toStatus() = ChildStatus(
    day = tag.toString(),
    allowance = AllowanceBreakdown(
        templateMinutes = tageskontingent.vorlage.toMinutes().toInt(),
        adjustmentMinutes = tageskontingent.anpassungen.toMinutes().toInt(),
        totalMinutes = tageskontingent.gesamt.toMinutes().toInt(),
    ),
    usedMillis = verbrauch.toMillis(),
    remainingMillis = restzeit.toMillis(),
    overrunMillis = ueberziehung.toMillis(),
    state = when (zustand) {
        Zustand.BEREIT -> ChildState.READY
        Zustand.LAEUFT -> ChildState.RUNNING
        Zustand.VORWARNUNG -> ChildState.WARNING
        Zustand.UEBERZIEHUNG -> ChildState.OVERRUN
        Zustand.AUFGEBRAUCHT -> ChildState.EXHAUSTED
    },
    runningSession = laufendeSitzung?.let { RunningSession(it.id, it.start.toEpochMilli()) },
)

private fun Sitzung.toRecord(offline: Set<String>) = SessionRecord(
    id = id,
    recordedOffline = id in offline,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = ende?.zeitpunkt?.toEpochMilli(),
    endReason = ende?.let { if (it.grund == Endegrund.AUTOMATISCHER_STOPP) SessionEndReason.AUTOMATIC_STOP else SessionEndReason.STOPPED },
)

private fun Wochenvorlage.dtoFor(tag: LocalDate): WeeklyTemplateVersion {
    val version = version(tag)
    return WeeklyTemplateVersion(
        validFrom = (version?.gueltigAb ?: LocalDate.EPOCH).toString(),
        minutes = DayOfWeek.entries.map { version?.minuten?.getValue(it) ?: 0 },
    )
}

sealed interface SyncResult {
    /** [changed]: the batch brought a new Start or Stopp (the Elterngeräte get a Resync-Signal). */
    data class Synced(val snapshot: ChildSnapshot, val changed: Boolean = false) : SyncResult

    data class Invalid(val reason: String) : SyncResult

    /** The id belongs to a Sitzung of another Kind: a Kindgerät may only upsert the Sitzungen of its own Kind. */
    data object ForeignSession : SyncResult
}
