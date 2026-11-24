package io.github.drusin.kidscreentime.child

import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionRecord
import io.github.drusin.kidscreentime.api.WeeklyTemplateVersion
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Endegrund
import io.github.drusin.kidscreentime.domain.bildschirmzeit.KindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Sitzung
import io.github.drusin.kidscreentime.domain.bildschirmzeit.SitzungsEnde
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesanpassung
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Wochenvorlage
import io.github.drusin.kidscreentime.domain.bildschirmzeit.WochenvorlageVersion
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Kindgerät calculates with `domain` and the last synced snapshot, exactly like the server. From the
 * Wochenvorlage it knows only the versions valid today and tomorrow, which is enough until the next snapshot.
 */
fun ChildSnapshot.toKindStand(pending: List<PendingSession> = emptyList()): KindStand = KindStand(
    zeitzone = ZoneId.of(timeZone),
    wochenvorlage = Wochenvorlage(listOf(templateToday, templateTomorrow).distinctBy { it.validFrom }.map { it.toDomain() }),
    tagesanpassungen = todayAdjustments.map {
        Tagesanpassung(LocalDate.parse(it.day), it.deltaMinutes, Instant.ofEpochMilli(it.bookedAtEpochMillis))
    },
    sitzungen = sessionsWith(pending),
    vorwarnschwelle = Duration.ofMinutes(warningThresholdMinutes.toLong()),
    ueberziehungsgrenze = Duration.ofMinutes(overrunLimitMinutes.toLong()),
)

/**
 * The Sitzungen of the snapshot with the Outbox on top. Like at the server the earlier end wins: a Sitzung the
 * snapshot shows as ended (for example after an Automatischer Stopp) stays as the server has it. A Sitzung that is
 * still open in the snapshot is replaced by its Outbox version (the Stopp pressed here); a new one is added.
 */
private fun ChildSnapshot.sessionsWith(pending: List<PendingSession>): List<Sitzung> {
    val fromServer = todaySessions.map { it.toDomain() }
    val endedAtServer = fromServer.filter { it.ende != null }.map { it.id }.toSet()
    val replaced = pending.map { it.id }.toSet() - endedAtServer
    val fromOutbox = pending.filter { it.id !in endedAtServer }.map { it.toDomain() }
    return (fromServer.filter { it.id !in replaced } + fromOutbox).sortedBy { it.start }
}

private fun SessionRecord.toDomain() = Sitzung(
    id = id,
    start = Instant.ofEpochMilli(startEpochMillis),
    ende = endEpochMillis?.let {
        SitzungsEnde(Instant.ofEpochMilli(it), if (endReason == SessionEndReason.AUTOMATIC_STOP) Endegrund.AUTOMATISCHER_STOPP else Endegrund.GESTOPPT)
    },
)

private fun PendingSession.toDomain() = Sitzung(
    id = id,
    start = Instant.ofEpochMilli(startWallMillis),
    // The wall clock may have jumped back between Start and Stopp: the end never lies before the start.
    ende = endWallMillis?.let { SitzungsEnde(Instant.ofEpochMilli(maxOf(it, startWallMillis)), Endegrund.GESTOPPT) },
)

private fun WeeklyTemplateVersion.toDomain() =
    WochenvorlageVersion(LocalDate.parse(validFrom), DayOfWeek.entries.zip(minutes).toMap())
