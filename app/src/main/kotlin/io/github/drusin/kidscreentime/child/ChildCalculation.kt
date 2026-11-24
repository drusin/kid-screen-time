package io.github.drusin.kidscreentime.child

import io.github.drusin.kidscreentime.api.ChildSnapshot
import io.github.drusin.kidscreentime.api.SessionEndReason
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
fun ChildSnapshot.toKindStand(): KindStand = KindStand(
    zeitzone = ZoneId.of(timeZone),
    wochenvorlage = Wochenvorlage(listOf(templateToday, templateTomorrow).distinctBy { it.validFrom }.map { it.toDomain() }),
    tagesanpassungen = todayAdjustments.map {
        Tagesanpassung(LocalDate.parse(it.day), it.deltaMinutes, Instant.ofEpochMilli(it.bookedAtEpochMillis))
    },
    sitzungen = todaySessions.map { record ->
        Sitzung(
            id = record.id,
            start = Instant.ofEpochMilli(record.startEpochMillis),
            ende = record.endEpochMillis?.let {
                SitzungsEnde(
                    Instant.ofEpochMilli(it),
                    if (record.endReason == SessionEndReason.AUTOMATIC_STOP) Endegrund.AUTOMATISCHER_STOPP else Endegrund.GESTOPPT,
                )
            },
        )
    },
    vorwarnschwelle = Duration.ofMinutes(warningThresholdMinutes.toLong()),
    ueberziehungsgrenze = Duration.ofMinutes(overrunLimitMinutes.toLong()),
)

private fun WeeklyTemplateVersion.toDomain() =
    WochenvorlageVersion(LocalDate.parse(validFrom), DayOfWeek.entries.zip(minutes).toMap())
