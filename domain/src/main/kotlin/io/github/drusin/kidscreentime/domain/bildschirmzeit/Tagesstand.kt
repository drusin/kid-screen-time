package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Das Tageskontingent mit Aufschlüsselung. [gesamt] ist Vorlage plus Anpassungen, mindestens 0. */
data class Tageskontingent(
    val vorlage: Duration,
    val anpassungen: Duration,
) {
    val gesamt: Duration = (vorlage + anpassungen).coerceAtLeast(Duration.ZERO)
}

enum class Zustand {
    /** Keine Sitzung läuft, Restzeit > 0. */
    BEREIT,

    /** Eine Sitzung läuft, Restzeit liegt über der Vorwarnschwelle. */
    LAEUFT,

    /** Eine Sitzung läuft, Restzeit > 0 und höchstens die Vorwarnschwelle. */
    VORWARNUNG,

    /** Eine Sitzung läuft bei Restzeit 0. */
    UEBERZIEHUNG,

    /** Keine Sitzung läuft, Restzeit 0. */
    AUFGEBRAUCHT,
}

/** Die für einen Zeitpunkt abgeleiteten Werte. Alle Dauern sind tatsächlich verstrichene Zeit. */
data class Tagesstand(
    /** Der Tag in der Familien-Zeitzone. */
    val tag: LocalDate,
    /** Beginn des nächsten Tages (nächste Mitternacht). */
    val tagesende: Instant,
    val tageskontingent: Tageskontingent,
    /** Heutiger Verbrauch (nur der Teil von Sitzungen, der in diesen Tag fällt). */
    val verbrauch: Duration,
    /** Tageskontingent minus Verbrauch, nie < 0. */
    val restzeit: Duration,
    /** Verbrauch über dem Tageskontingent, nie < 0. */
    val ueberziehung: Duration,
    val zustand: Zustand,
    val laufendeSitzung: Sitzung?,
) {
    /** Ein Start ist nur bei Restzeit > 0 erlaubt (und nur, wenn keine Sitzung läuft). */
    val startErlaubt: Boolean get() = laufendeSitzung == null && restzeit > Duration.ZERO
}
