package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    val vorwarnschwelle: Duration = STANDARD_VORWARNSCHWELLE,
    val ueberziehungsgrenze: Duration = STANDARD_UEBERZIEHUNGSGRENZE,
) {
    companion object {
        /** Standard für ein neues Kind: Vorwarnung bei 5 min Restzeit. */
        val STANDARD_VORWARNSCHWELLE: Duration = Duration.ofMinutes(5)

        /** Standard für ein neues Kind: Automatischer Stopp nach 60 min Überziehung. */
        val STANDARD_UEBERZIEHUNGSGRENZE: Duration = Duration.ofMinutes(60)
    }

    /** Die abgeleiteten Werte für den Tag, in den [jetzt] fällt. */
    fun tagesstand(jetzt: Instant): Tagesstand {
        val tag = jetzt.atZone(zeitzone).toLocalDate()
        val rechnung = rechne(tag, jetzt)
        val kontingent = rechnung.kontingent
        val verbrauch = rechnung.verbrauch

        val restzeit = (kontingent.gesamt - verbrauch).coerceAtLeast(Duration.ZERO)
        val ueberziehung = (verbrauch - kontingent.gesamt).coerceAtLeast(Duration.ZERO)
        val laufend = sitzungen.firstOrNull { it.laeuftUm(jetzt) }

        val zustand = when {
            laufend == null -> if (restzeit > Duration.ZERO) Zustand.BEREIT else Zustand.AUFGEBRAUCHT
            restzeit.isZero -> Zustand.UEBERZIEHUNG
            restzeit <= vorwarnschwelle -> Zustand.VORWARNUNG
            else -> Zustand.LAEUFT
        }
        return Tagesstand(tag, rechnung.tagesende, kontingent, verbrauch, restzeit, ueberziehung, zustand, laufend)
    }

    /** Kontingent und Verbrauch von [tag], gesehen zum Zeitpunkt [jetzt] (spätere Buchungen und Sitzungsteile zählen nicht). */
    private class Tagesrechnung(val tagesbeginn: Instant, val tagesende: Instant, val kontingent: Tageskontingent, val verbrauch: Duration)

    /** Die eine Rechnung für [tagesstand] und [verlauf]: für einen vergangenen Tag ist [jetzt] sein Ende. */
    private fun rechne(tag: LocalDate, jetzt: Instant): Tagesrechnung {
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
        return Tagesrechnung(tagesbeginn, tagesende, kontingent, verbrauch)
    }

    /**
     * Der Verlauf: die letzten [tage] Tage bis [jetzt], jüngster zuerst. Mit [mitHeute] `false` beginnt er gestern (die
     * „letzten Tage“ der Startseite). Jeder Tag trägt Verbrauch, Tageskontingent (die Vorlagen-Version, die um 00:00 des
     * Tages galt, plus die Anpassungen dieses Tages), Überziehung, seine Sitzungen (an Mitternacht aufgeteilt, wie in
     * [tagesstand]) und seine Tagesanpassungen. Tage ohne alles stehen auch drin.
     */
    fun verlauf(jetzt: Instant, tage: Int, mitHeute: Boolean = true): List<Verlaufstag> {
        val heute = jetzt.atZone(zeitzone).toLocalDate()
        val ersterOffset = if (mitHeute) 0L else 1L
        return (ersterOffset until ersterOffset + tage).map { heute.minusDays(it) }.map { tag ->
            val rechnung = rechne(tag, jetzt)
            val bis = minOf(rechnung.tagesende, jetzt)
            val gesamt = rechnung.kontingent.gesamt
            Verlaufstag(
                tag = tag,
                tageskontingent = rechnung.kontingent,
                verbrauch = rechnung.verbrauch,
                ueberziehung = (rechnung.verbrauch - gesamt).coerceAtLeast(Duration.ZERO),
                sitzungen = sitzungen
                    .filter { !it.start.isAfter(jetzt) && it.start < rechnung.tagesende && (it.start >= rechnung.tagesbeginn || (it.ende?.zeitpunkt ?: bis) > rechnung.tagesbeginn) }
                    .sortedWith(compareBy({ it.start }, { it.id }))
                    .map { it.imTag(rechnung.tagesbeginn, rechnung.tagesende, jetzt) },
                anpassungen = tagesanpassungen
                    .filter { it.tag == tag && !it.gebuchtAm.isAfter(jetzt) }
                    .sortedBy { it.gebuchtAm }
                    .map { anpassung ->
                        VerlaufAnpassung(
                            deltaMinuten = anpassung.deltaMinuten,
                            gebuchtAm = anpassung.gebuchtAm,
                            istGegenbuchung = anpassung.gegenbuchungZu != null,
                            zurueckgenommen = anpassung.id != null && tagesanpassungen.any { it.gegenbuchungZu == anpassung.id },
                        )
                    },
            )
        }
    }
}

/** Der Teil der Sitzung, der in [tagesbeginn, tagesende) fällt. Ohne Ende läuft sie zum Zeitpunkt [jetzt] noch. */
private fun Sitzung.imTag(tagesbeginn: Instant, tagesende: Instant, jetzt: Instant): VerlaufSitzung {
    val echtesEnde = ende?.zeitpunkt
    val setztSichFort = echtesEnde == null && jetzt >= tagesende || echtesEnde != null && echtesEnde > tagesende
    val von = maxOf(start, tagesbeginn)
    val bis = minOf(echtesEnde ?: jetzt, tagesende)
    return VerlaufSitzung(
        id = id,
        von = von,
        bis = if (echtesEnde == null && !setztSichFort) null else bis,
        dauer = if (bis.isAfter(von)) Duration.between(von, bis) else Duration.ZERO,
        vomVortag = start < tagesbeginn,
        setztSichFort = setztSichFort,
        endegrund = if (!setztSichFort) ende?.grund else null,
        offlineErfasst = offlineErfasst,
    )
}
