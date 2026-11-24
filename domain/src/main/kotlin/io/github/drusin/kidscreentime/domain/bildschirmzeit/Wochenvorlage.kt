package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

/**
 * Eine Version der Wochenvorlage: Standard-Kontingent in Minuten je Wochentag, gültig ab 00:00 des Tages
 * [gueltigAb] (Datum in der Familien-Zeitzone). Es müssen alle sieben Wochentage belegt sein.
 */
data class WochenvorlageVersion(
    val gueltigAb: LocalDate,
    val minuten: Map<DayOfWeek, Int>,
) {
    init {
        require(minuten.keys == DayOfWeek.entries.toSet()) { "Alle sieben Wochentage brauchen einen Wert: ${minuten.keys}" }
        require(minuten.values.all { it >= 0 }) { "Minuten der Wochenvorlage dürfen nicht negativ sein: $minuten" }
    }

    companion object {
        /** Bequemer Konstruktor: Werte von Montag bis Sonntag. */
        fun ausMontagBisSonntag(gueltigAb: LocalDate, vararg minuten: Int): WochenvorlageVersion {
            require(minuten.size == 7) { "Erwartet sieben Werte (Mo–So), bekommen ${minuten.size}" }
            return WochenvorlageVersion(gueltigAb, DayOfWeek.entries.zip(minuten.toList()).toMap())
        }

        /** Standardwerte für ein neues Kind: Werktage 60 min, Samstag und Sonntag 120 min. */
        fun standard(gueltigAb: LocalDate): WochenvorlageVersion =
            ausMontagBisSonntag(gueltigAb, 60, 60, 60, 60, 60, 120, 120)

        /** An jedem Wochentag derselbe Wert. */
        fun gleichbleibend(gueltigAb: LocalDate, minuten: Int): WochenvorlageVersion =
            WochenvorlageVersion(gueltigAb, DayOfWeek.entries.associateWith { minuten })
    }
}

/**
 * Die versionierte Wochenvorlage eines Kindes. Eine Änderung ist eine neue Version mit „gültig ab“ (in der
 * Regel morgen), die alten Versionen bleiben erhalten, damit vergangene Tage richtig berechnet werden.
 */
data class Wochenvorlage(val versionen: List<WochenvorlageVersion>) {
    init {
        require(versionen.map { it.gueltigAb }.distinct().size == versionen.size) {
            "Zwei Versionen mit demselben „gültig ab“-Datum"
        }
    }

    /**
     * Wert für [tag]: die Version, die um 00:00 dieses Tages galt (jüngstes „gültig ab“ <= [tag]).
     * Vor der ersten Version ist der Wert 0.
     */
    fun fuer(tag: LocalDate): Duration {
        val version = version(tag) ?: return Duration.ZERO
        return Duration.ofMinutes(version.minuten.getValue(tag.dayOfWeek).toLong())
    }

    /** Die Version, die um 00:00 von [tag] galt, oder null vor der ersten Version. */
    fun version(tag: LocalDate): WochenvorlageVersion? =
        versionen.filter { it.gueltigAb <= tag }.maxByOrNull { it.gueltigAb }

    companion object {
        fun von(vararg versionen: WochenvorlageVersion) = Wochenvorlage(versionen.toList())
    }
}
