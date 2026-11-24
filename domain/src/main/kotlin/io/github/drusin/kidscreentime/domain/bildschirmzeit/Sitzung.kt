package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Instant

enum class Endegrund {
    /** Das Kind hat gestoppt. */
    GESTOPPT,

    /** Die Überziehungsgrenze wurde erreicht (Automatischer Stopp). */
    AUTOMATISCHER_STOPP,
}

data class SitzungsEnde(val zeitpunkt: Instant, val grund: Endegrund)

/** Ein Timer-Lauf. Ohne [ende] ist die Sitzung offen (läuft noch). */
data class Sitzung(
    val id: String,
    val start: Instant,
    val ende: SitzungsEnde? = null,
) {
    init {
        require(ende == null || !ende.zeitpunkt.isBefore(start)) { "Sitzung $id endet vor ihrem Start" }
    }

    /** Läuft die Sitzung zum Zeitpunkt [jetzt]? Ein Ende in der Zukunft zählt noch als laufend. */
    fun laeuftUm(jetzt: Instant): Boolean = !start.isAfter(jetzt) && (ende == null || ende.zeitpunkt.isAfter(jetzt))
}
