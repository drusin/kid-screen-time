package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Der Stand eines Kindes: alles, was `domain` zum Rechnen braucht. Reine Daten, kein I/O, keine eigene Uhr:
 * Die Werte für einen Zeitpunkt liefert [tagesstand], der Zeitpunkt wird übergeben (aus `WallClock`).
 */
data class KindStand(
    val zeitzone: ZoneId,
    val wochenvorlage: Wochenvorlage,
    val tagesanpassungen: List<Tagesanpassung> = emptyList(),
    val sitzungen: List<Sitzung> = emptyList(),
    val vorwarnschwelle: Duration = Duration.ofMinutes(5),
    val ueberziehungsgrenze: Duration = Duration.ofMinutes(60),
) {
    /** Die abgeleiteten Werte für den Tag, in den [jetzt] fällt. */
    fun tagesstand(jetzt: Instant): Tagesstand {
        val tag = jetzt.atZone(zeitzone).toLocalDate()
        val tagesbeginn = tag.atStartOfDay(zeitzone).toInstant()
        val tagesende = tag.plusDays(1).atStartOfDay(zeitzone).toInstant()

        val anpassungen = tagesanpassungen
            .filter { it.tag == tag && !it.gebuchtAm.isAfter(jetzt) }
            .sumOf { it.deltaMinuten.toLong() }
        val kontingent = Tageskontingent(wochenvorlage.fuer(tag), Duration.ofMinutes(anpassungen))

        // Verbrauch: Überlappung jeder Sitzung mit [Tagesbeginn, min(Tagesende, jetzt)).
        val bis = minOf(tagesende, jetzt)
        val verbrauch = sitzungen.fold(Duration.ZERO) { summe, sitzung ->
            val von = maxOf(sitzung.start, tagesbeginn)
            val sitzungBis = minOf(sitzung.ende?.zeitpunkt ?: bis, bis)
            if (sitzungBis.isAfter(von)) summe + Duration.between(von, sitzungBis) else summe
        }

        val restzeit = (kontingent.gesamt - verbrauch).coerceAtLeast(Duration.ZERO)
        val ueberziehung = (verbrauch - kontingent.gesamt).coerceAtLeast(Duration.ZERO)
        val laufend = sitzungen.firstOrNull { it.laeuftUm(jetzt) }

        val zustand = when {
            laufend == null -> if (restzeit > Duration.ZERO) Zustand.BEREIT else Zustand.AUFGEBRAUCHT
            restzeit.isZero -> Zustand.UEBERZIEHUNG
            restzeit <= vorwarnschwelle -> Zustand.VORWARNUNG
            else -> Zustand.LAEUFT
        }
        return Tagesstand(tag, tagesende, kontingent, verbrauch, restzeit, ueberziehung, zustand, laufend)
    }
}
