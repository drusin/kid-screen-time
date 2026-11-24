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
import io.github.drusin.kidscreentime.domain.clock.WallClock
import java.sql.Connection
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Kinder: creating them (with the Standardwerte) and reading their state. The values come from `domain`
 * ([KindStand.tagesstand]); this class only loads the input from the database and maps the result to the DTOs.
 */
class ChildService(
    private val database: Database,
    private val wallClock: WallClock,
    private val zone: ZoneId,
) {
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
     * - The earlier end wins: a stop for an already ended Sitzung (e.g. after an Automatischer Stopp) changes nothing.
     */
    fun sync(childId: String, updates: List<SessionUpdate>): SyncResult {
        val invalid = updates.firstOrNull { runCatching { UUID.fromString(it.id) }.isFailure }
        if (invalid != null) return SyncResult.Invalid("Ungültige Sitzungs-ID: ${invalid.id}")
        val now = wallClock.now()
        return try {
            database.transaction { c ->
                for (update in updates.sortedBy { -it.startAgeMillis }) {
                    if (!upsert(c, childId, update, now)) throw ForeignSessionException()
                }
                val snapshot = checkNotNull(snapshot(c, childId, now)) { "Das Kind des Kindgeräts fehlt" }
                SyncResult.Synced(snapshot)
            }
        } catch (_: ForeignSessionException) {
            // Thrown inside the transaction on purpose: everything of this batch is rolled back.
            SyncResult.ForeignSession
        }
    }

    private class ForeignSessionException : RuntimeException()

    /** Returns false when the id belongs to a Sitzung of another Kind. */
    private fun upsert(c: Connection, childId: String, update: SessionUpdate, now: Instant): Boolean {
        val start = now.minusMillis(update.startAgeMillis.coerceAtLeast(0))
        val existing = c.prepareStatement("SELECT child_id, end_at FROM sessions WHERE id = ?").use { st ->
            st.setString(1, update.id)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getLong(2).takeUnless { rs.wasNull() } else null }
        }
        if (existing == null) {
            c.prepareStatement("INSERT INTO sessions (id, child_id, start_at) VALUES (?,?,?)").use {
                it.setString(1, update.id)
                it.setString(2, childId)
                it.setLong(3, start.toEpochMilli())
                it.executeUpdate()
            }
        } else if (existing.first != childId) {
            return false
        }
        val endAge = update.endAgeMillis
        if (endAge != null && (existing == null || existing.second == null)) {
            c.prepareStatement(
                // The end never lies before the start, whichever start the Sitzung really has.
                "UPDATE sessions SET end_at = MAX(?, start_at), end_reason = 'STOPPED' WHERE id = ? AND end_at IS NULL",
            ).use {
                it.setLong(1, now.minusMillis(endAge.coerceAtLeast(0)).toEpochMilli())
                it.setString(2, update.id)
                it.executeUpdate()
            }
        }
        return true
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
            todaySessions = stand.sitzungen.map { it.toRecord() },
            todayAdjustments = stand.tagesanpassungen.map { DayAdjustment(it.tag.toString(), it.deltaMinuten, it.gebuchtAm.toEpochMilli()) },
        )
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
            )
        }
    }

    private class Loaded(val name: String, val stand: KindStand)

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
        return Loaded(name, stand)
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

private fun Sitzung.toRecord() = SessionRecord(
    id = id,
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
    data class Synced(val snapshot: ChildSnapshot) : SyncResult

    data class Invalid(val reason: String) : SyncResult

    /** The id belongs to a Sitzung of another Kind: a Kindgerät may only upsert the Sitzungen of its own Kind. */
    data object ForeignSession : SyncResult
}
