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
/**
 * What the Kindgerät calculates with: the stored snapshot plus the Outbox, all in the time of this device. Screen and
 * Alarmkern both use it and compare with the device's own wall clock.
 */
fun ChildLocalState.toKindStand(): KindStand = stored.snapshot.toKindStand(pending, stored.serverToDevice)

/**
 * [serverToDevice] moves the server times of the snapshot (Sitzungen, Tagesanpassungen) onto the clock of this device;
 * the Outbox ([pending]) is already in device time. Zero keeps server time (for comparisons with the server's clock).
 */
fun ChildSnapshot.toKindStand(pending: List<PendingSession> = emptyList(), serverToDevice: Duration = Duration.ZERO): KindStand = KindStand(
    zeitzone = ZoneId.of(timeZone),
    wochenvorlage = Wochenvorlage(listOf(templateToday, templateTomorrow).distinctBy { it.validFrom }.map { it.toDomain() }),
    tagesanpassungen = todayAdjustments.map {
        Tagesanpassung(LocalDate.parse(it.day), it.deltaMinutes, Instant.ofEpochMilli(it.bookedAtEpochMillis) + serverToDevice)
    },
    sitzungen = sessionsWith(pending, serverToDevice),
    vorwarnschwelle = Duration.ofMinutes(warningThresholdMinutes.toLong()),
    ueberziehungsgrenze = Duration.ofMinutes(overrunLimitMinutes.toLong()),
)

/**
 * The Sitzungen of the snapshot with the Outbox on top. Like at the server the earlier end wins, in time: a Sitzung
 * the snapshot shows as ended (for example after an Automatischer Stopp) stays as the server has it, unless the
 * Outbox ends it earlier (the child stopped offline before the server did). A Sitzung that is still open in the
 * snapshot is replaced by its Outbox version (the Stopp pressed here); a new one is added.
 */
private fun ChildSnapshot.sessionsWith(pending: List<PendingSession>, serverToDevice: Duration): List<Sitzung> {
    val fromServer = todaySessions.map { it.toDomain(serverToDevice) }
    val endAtServer = fromServer.mapNotNull { s -> s.ende?.let { s.id to it.zeitpunkt } }.toMap()
    // Outbox wins when the server has no end yet or this device ended the Sitzung earlier.
    val fromOutbox = pending.map { it.toDomain() }.filter { p ->
        val serverEnd = endAtServer[p.id]
        val end = p.ende?.zeitpunkt
        serverEnd == null || (end != null && end.isBefore(serverEnd))
    }
    val replaced = fromOutbox.map { it.id }.toSet()
    return (fromServer.filter { it.id !in replaced } + fromOutbox).sortedBy { it.start }
}

private fun SessionRecord.toDomain(serverToDevice: Duration) = Sitzung(
    id = id,
    start = Instant.ofEpochMilli(startEpochMillis) + serverToDevice,
    ende = endEpochMillis?.let {
        SitzungsEnde(Instant.ofEpochMilli(it) + serverToDevice, if (endReason == SessionEndReason.AUTOMATIC_STOP) Endegrund.AUTOMATISCHER_STOPP else Endegrund.GESTOPPT)
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
