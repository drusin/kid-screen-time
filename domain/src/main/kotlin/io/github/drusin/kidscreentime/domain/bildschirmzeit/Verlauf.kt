package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Ein Tag im Verlauf (Tag in der Familien-Zeitzone). [tageskontingent] ist das Kontingent dieses Tages (Vorlage, die um
 * 00:00 galt, plus Anpassungen), [verbrauch] nur der Teil der Sitzungen, der in diesen Tag fällt.
 */
data class Verlaufstag(
    val tag: LocalDate,
    val tageskontingent: Tageskontingent,
    val verbrauch: Duration,
    /** Verbrauch über dem Tageskontingent, nie < 0. */
    val ueberziehung: Duration,
    /** Die Sitzungen dieses Tages in Startreihenfolge, an Mitternacht aufgeteilt. */
    val sitzungen: List<VerlaufSitzung>,
    /** Die Tagesanpassungen dieses Tages in Buchungsreihenfolge. */
    val anpassungen: List<VerlaufAnpassung>,
)

/**
 * Der Teil einer Sitzung, der in einen Tag fällt: [von] bis [bis] (am Tagesanfang beziehungsweise Tagesende
 * abgeschnitten, [bis] ist null, solange die Sitzung heute noch läuft).
 */
data class VerlaufSitzung(
    val id: String,
    val von: Instant,
    val bis: Instant?,
    /** Verstrichene Zeit dieses Teils. */
    val dauer: Duration,
    /** Die Sitzung hat vor 00:00 dieses Tages begonnen. */
    val vomVortag: Boolean,
    /** Die Sitzung läuft über 00:00 des nächsten Tages hinaus (oder läuft dann noch). */
    val setztSichFort: Boolean,
    /** Warum die Sitzung endete, nur am Tag ihres Endes ([AUTOMATISCHER_STOPP] = „Automatisch gestoppt“); sonst null. */
    val endegrund: Endegrund?,
    /** „offline erfasst“. */
    val offlineErfasst: Boolean,
)

/** Eine Tagesanpassung im Verlauf. [zurueckgenommen]: eine spätere Gegenbuchung hebt sie wieder auf. */
data class VerlaufAnpassung(
    val deltaMinuten: Int,
    val gebuchtAm: Instant,
    /** Diese Buchung ist selbst eine Gegenbuchung (nimmt eine andere zurück). */
    val istGegenbuchung: Boolean,
    val zurueckgenommen: Boolean,
)
