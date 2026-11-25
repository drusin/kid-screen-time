package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Instant
import java.time.LocalDate

/**
 * Zu- oder Abschlag in Minuten auf das Tageskontingent von [tag] (Datum in der Familien-Zeitzone).
 * Eine Gegenbuchung ist eine weitere Tagesanpassung mit umgekehrtem Vorzeichen. [gebuchtAm] ist der
 * Zeitpunkt der Buchung: Wer den Stand zu einem früheren Zeitpunkt berechnet, sieht spätere Buchungen nicht.
 *
 * [id] und [gegenbuchungZu] sind nur für den Verlauf: Eine Gegenbuchung nennt die [id] der Buchung, die sie
 * zurücknimmt. Die Rechnung des Kontingents summiert nur die Deltas.
 */
data class Tagesanpassung(
    val tag: LocalDate,
    val deltaMinuten: Int,
    val gebuchtAm: Instant,
    val id: String? = null,
    val gegenbuchungZu: String? = null,
)
