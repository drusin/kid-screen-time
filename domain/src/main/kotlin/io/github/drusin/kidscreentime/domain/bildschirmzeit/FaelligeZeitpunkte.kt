package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant

/**
 * Was am Kindgerät zu einem Zeitpunkt passieren muss. Spätere Auslöser (Automatischer Stopp, Mitternacht) kommen als
 * weitere Einträge hierher; der Alarmkern des Kindgeräts muss sie dann nur noch behandeln (das `when` darüber ist
 * erschöpfend).
 */
enum class Ausloeser {
    /** Kurzer Ton: die Restzeit erreicht die Vorwarnschwelle. */
    VORWARNUNG,

    /** Klingeln: die Restzeit ist 0, danach alle [FaelligeZeitpunkte.ALARMWIEDERHOLUNG]. */
    ALARM,

    /**
     * Die Sitzung endet ohne Zutun des Kindes: die Überziehung erreicht die Überziehungsgrenze. Der Zeitpunkt ist das
     * Ende der Sitzung, auch wenn er schon vorbei ist (Gerät oder Server war nicht erreichbar).
     */
    AUTOMATISCHER_STOPP,
}

data class FaelligerZeitpunkt(val ausloeser: Ausloeser, val zeitpunkt: Instant)

/**
 * Was das Kindgerät schon getan hat, solange die laufende Sitzung läuft. `domain` kennt keine Geschichte, sie kommt vom
 * Aufrufer (der Alarmkern merkt sie sich je Sitzung) und gilt nur für diese eine Sitzung.
 */
data class Alarmverlauf(
    /** Die Vorwarnung kam in dieser Sitzung schon (sie kommt höchstens einmal pro Sitzung). */
    val vorwarnungGegeben: Boolean = false,
    /** Wann der Alarm zuletzt geklingelt hat. */
    val letzterAlarm: Instant? = null,
) {
    companion object {
        val KEINER = Alarmverlauf()
    }
}

/** Die nächsten fälligen Zeitpunkte, nach Zeit sortiert. Ein Zeitpunkt nicht nach `jetzt` ist sofort fällig. */
data class FaelligeZeitpunkte(val liste: List<FaelligerZeitpunkt>) {
    companion object {
        /** Der Alarm wiederholt sich fest alle 5 Minuten. */
        val ALARMWIEDERHOLUNG: Duration = Duration.ofMinutes(5)
    }

    /** Der Zeitpunkt des Automatischen Stopps, null wenn keine Sitzung läuft oder er nicht mehr an diesem Tag liegt. */
    val automatischerStopp: Instant? get() = liste.firstOrNull { it.ausloeser == Ausloeser.AUTOMATISCHER_STOPP }?.zeitpunkt

    /** Der früheste Zeitpunkt, null wenn nichts mehr ansteht. */
    val naechster: FaelligerZeitpunkt? get() = liste.firstOrNull()

    /** Alles, was bis einschließlich [jetzt] fällig ist. */
    fun faelligBis(jetzt: Instant): List<FaelligerZeitpunkt> = liste.filter { !it.zeitpunkt.isAfter(jetzt) }
}

/**
 * Die nächsten fälligen Zeitpunkte für den Tag von [jetzt]. Nur während einer laufenden Sitzung steht etwas an.
 *
 * - Vorwarnung: wenn die Restzeit die Vorwarnschwelle erreicht, sofort, wenn sie schon darunter (aber > 0) liegt;
 *   nie, wenn [verlauf] sie schon verzeichnet.
 * - Alarm: wenn die Restzeit 0 erreicht, sofort bei Restzeit 0. Danach alle 5 Minuten nach dem letzten Klingeln.
 * - Automatischer Stopp: wenn die Überziehung die Überziehungsgrenze erreicht (Ende der Sitzung). Alles, was nicht vor
 *   dem Stopp liegt, entfällt. Ist der Stopp schon vorbei, steht er mit seinem tatsächlichen Zeitpunkt (nicht `jetzt`) an.
 * - Zeitpunkte nach Tagesende (Mitternacht) gibt es nicht: dann gilt ein neues Tageskontingent, und die Neuplanung
 *   zu Mitternacht (Ticket 11) rechnet neu.
 */
fun KindStand.faelligeZeitpunkte(jetzt: Instant, verlauf: Alarmverlauf = Alarmverlauf.KEINER): FaelligeZeitpunkte {
    val stand = tagesstand(jetzt)
    val sitzung = stand.laufendeSitzung ?: return FaelligeZeitpunkte(emptyList())

    // Die Überziehung wächst mit der Zeit 1:1. Sie erreicht die Grenze nach (Restzeit + Grenze − bisherige Überziehung).
    // Lag die Überziehung schon vor dem Start dieser Sitzung über der Grenze, endet die Sitzung mit ihrem Start.
    val stopp = maxOf(jetzt + stand.restzeit - stand.ueberziehung + ueberziehungsgrenze, sitzung.start)

    val liste = mutableListOf<FaelligerZeitpunkt>()
    if (stand.restzeit > Duration.ZERO) {
        if (!verlauf.vorwarnungGegeben) {
            val bisVorwarnung = (stand.restzeit - vorwarnschwelle).coerceAtLeast(Duration.ZERO)
            liste += FaelligerZeitpunkt(Ausloeser.VORWARNUNG, jetzt + bisVorwarnung)
        }
        liste += FaelligerZeitpunkt(Ausloeser.ALARM, jetzt + stand.restzeit)
    } else {
        // Die Restzeit erreichte 0 vor `ueberziehung`; ein Klingeln davor gehörte zu einem früheren Erreichen.
        val ueberziehungSeit = jetzt - stand.ueberziehung
        val letzter = verlauf.letzterAlarm?.takeIf { !it.isBefore(ueberziehungSeit) }
        val naechster = letzter?.plus(FaelligeZeitpunkte.ALARMWIEDERHOLUNG)?.takeIf { it.isAfter(jetzt) } ?: jetzt
        liste += FaelligerZeitpunkt(Ausloeser.ALARM, naechster)
    }
    // Mit dem Stopp endet die Sitzung: danach klingelt nichts mehr.
    liste.removeAll { it.zeitpunkt >= stopp }
    liste += FaelligerZeitpunkt(Ausloeser.AUTOMATISCHER_STOPP, stopp)
    return FaelligeZeitpunkte(liste.filter { !it.zeitpunkt.isAfter(stand.tagesende) }.sortedBy { it.zeitpunkt })
}
