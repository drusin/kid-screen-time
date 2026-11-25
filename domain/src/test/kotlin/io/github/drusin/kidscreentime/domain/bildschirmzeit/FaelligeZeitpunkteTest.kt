package io.github.drusin.kidscreentime.domain.bildschirmzeit

import io.github.drusin.kidscreentime.domain.clock.ManualClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaelligeZeitpunkteTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // Montag, 5. Januar 2026, 09:00 in Berlin.
    private val clock = ManualClock(Instant.parse("2026-01-05T08:00:00Z"))

    private fun minuten(n: Long): Duration = Duration.ofMinutes(n)

    private fun stand(
        kontingentMinuten: Int = 60,
        sitzungen: List<Sitzung> = emptyList(),
        anpassungen: List<Tagesanpassung> = emptyList(),
        vorwarnschwelle: Duration = minuten(5),
        ueberziehungsgrenze: Duration = minuten(60),
    ) = KindStand(
        zeitzone = berlin,
        wochenvorlage = Wochenvorlage(listOf(WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), kontingentMinuten))),
        tagesanpassungen = anpassungen,
        sitzungen = sitzungen,
        vorwarnschwelle = vorwarnschwelle,
        ueberziehungsgrenze = ueberziehungsgrenze,
    )

    private fun laufend(seitMinuten: Long = 0, id: String = "s1") = Sitzung(id, clock.now() - minuten(seitMinuten))

    private fun KindStand.zeitpunkte(verlauf: Alarmverlauf = Alarmverlauf.KEINER) = faelligeZeitpunkte(clock.now(), verlauf)

    private fun FaelligeZeitpunkte.von(ausloeser: Ausloeser) = liste.firstOrNull { it.ausloeser == ausloeser }?.zeitpunkt

    @Test
    fun `Ohne laufende Sitzung steht nichts an`() {
        assertTrue(stand().zeitpunkte().liste.isEmpty())
    }

    @Test
    fun `Vorwarnung kommt, wenn die Restzeit die Schwelle erreicht, der Alarm bei Restzeit 0`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend())).zeitpunkte()

        assertEquals(clock.now() + minuten(55), zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(60), zeitpunkte.von(Ausloeser.ALARM))
        assertEquals(Ausloeser.VORWARNUNG, zeitpunkte.naechster?.ausloeser)
    }

    @Test
    fun `Liegt die Restzeit schon unter der Vorwarnschwelle, kommt die Vorwarnung sofort`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 57))).zeitpunkte()

        assertEquals(clock.now(), zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(3), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Die Vorwarnung kommt für dasselbe Ende des Kontingents nur einmal`() {
        // Gewarnt wurde für das Ende in 3 min.
        val gegeben = Alarmverlauf(vorwarnungFuer = clock.now() + minuten(3))

        val zeitpunkte = stand(sitzungen = listOf(laufend(seitMinuten = 57))).zeitpunkte(gegeben)

        assertNull(zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(3), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Nach einem Bonus kommt die Vorwarnung wieder 5 min vor dem neuen Ende`() {
        // Gewarnt wurde für das alte Ende in 3 min, dann kamen 30 min Bonus.
        val gegeben = Alarmverlauf(vorwarnungFuer = clock.now() + minuten(3))
        val bonus = Tagesanpassung(LocalDate.of(2026, 1, 5), 30, clock.now())

        val zeitpunkte = stand(sitzungen = listOf(laufend(seitMinuten = 57)), anpassungen = listOf(bonus)).zeitpunkte(gegeben)

        assertEquals(clock.now() + minuten(28), zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(33), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Ein Abzug nach der Vorwarnung löst keine zweite aus`() {
        // Gewarnt wurde für das Ende in 3 min, ein Abzug von 2 min zieht es auf 1 min vor.
        val gegeben = Alarmverlauf(vorwarnungFuer = clock.now() + minuten(3))
        val abzug = Tagesanpassung(LocalDate.of(2026, 1, 5), -2, clock.now())

        val zeitpunkte = stand(sitzungen = listOf(laufend(seitMinuten = 57)), anpassungen = listOf(abzug)).zeitpunkte(gegeben)

        assertNull(zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(1), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Eine Tagesanpassung über die Schwelle verschiebt die Vorwarnung nach hinten`() {
        val bonus = Tagesanpassung(LocalDate.of(2026, 1, 5), 30, clock.now())

        val zeitpunkte = stand(sitzungen = listOf(laufend(seitMinuten = 57)), anpassungen = listOf(bonus)).zeitpunkte()

        assertEquals(clock.now() + minuten(28), zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(33), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Bei Restzeit 0 klingelt der Alarm sofort`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 60))).zeitpunkte()

        assertEquals(clock.now(), zeitpunkte.von(Ausloeser.ALARM))
        assertNull(zeitpunkte.von(Ausloeser.VORWARNUNG))
    }

    @Test
    fun `In der Überziehung klingelt der Alarm sofort, wenn noch nie geklingelt hat`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 75))).zeitpunkte()

        assertEquals(clock.now(), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Der Alarm wiederholt sich fest alle 5 Minuten nach dem letzten Klingeln`() {
        val sitzungen = listOf(laufend(seitMinuten = 62))
        val geklingeltVor2Min = Alarmverlauf(vorwarnungFuer = clock.now() - minuten(2), letzterAlarm = clock.now() - minuten(2))

        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = sitzungen).zeitpunkte(geklingeltVor2Min)

        assertEquals(clock.now() + minuten(3), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Ist die Wiederholung überfällig, klingelt der Alarm sofort`() {
        val sitzungen = listOf(laufend(seitMinuten = 70))
        val geklingeltVor9Min = Alarmverlauf(letzterAlarm = clock.now() - minuten(9))

        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = sitzungen).zeitpunkte(geklingeltVor9Min)

        assertEquals(clock.now(), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Ein Klingeln vor dem letzten Erreichen von Restzeit 0 zählt für die Wiederholung nicht`() {
        // Um 08:50 klingelte es, dann gab es eine Anpassung, und die Restzeit ist erst um 08:58 wieder 0 geworden.
        val sitzungen = listOf(laufend(seitMinuten = 62))
        val altesKlingeln = Alarmverlauf(letzterAlarm = clock.now() - minuten(10))
        val bonus = Tagesanpassung(LocalDate.of(2026, 1, 5), 0, clock.now())

        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = sitzungen, anpassungen = listOf(bonus))
            .zeitpunkte(altesKlingeln)

        assertEquals(clock.now(), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Eine Tagesanpassung bei Restzeit 0 über 0 verstummt den Alarm und plant ihn neu`() {
        val sitzungen = listOf(laufend(seitMinuten = 65))
        val bonus = Tagesanpassung(LocalDate.of(2026, 1, 5), 20, clock.now())

        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = sitzungen, anpassungen = listOf(bonus))
            .zeitpunkte(Alarmverlauf(vorwarnungFuer = clock.now() - minuten(5), letzterAlarm = clock.now() - minuten(1)))

        assertEquals(clock.now() + minuten(15), zeitpunkte.von(Ausloeser.ALARM))
        assertEquals(clock.now() + minuten(10), zeitpunkte.von(Ausloeser.VORWARNUNG), "neues Ende: wieder 5 min vorher warnen")
    }

    @Test
    fun `Eine Tagesanpassung unter den Verbrauch lässt den Alarm sofort klingeln`() {
        val abzug = Tagesanpassung(LocalDate.of(2026, 1, 5), -30, clock.now())

        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 40)), anpassungen = listOf(abzug)).zeitpunkte()

        assertEquals(clock.now(), zeitpunkte.von(Ausloeser.ALARM))
    }

    @Test
    fun `Nach Mitternacht gilt ein neues Kontingent, davor liegt kein Zeitpunkt jenseits des Tagesendes`() {
        // 23:50 in Berlin, 60 min Restzeit: die Restzeit reicht über Mitternacht, der Zeitpunkt läge im neuen Tag.
        clock.advance(Duration.ofMinutes(14 * 60 + 50))
        val zeitpunkte = stand(kontingentMinuten = 120, sitzungen = listOf(Sitzung("s1", clock.now() - minuten(60)))).zeitpunkte()

        assertEquals(listOf(Ausloeser.MITTERNACHT), zeitpunkte.liste.map { it.ausloeser }, "nur Mitternacht: dann wird neu gerechnet")
        assertEquals(clock.now() + minuten(10), zeitpunkte.naechster?.zeitpunkt)
    }

    @Test
    fun `Die fälligen Zeitpunkte bis jetzt sind genau die, die nicht in der Zukunft liegen`() {
        val zeitpunkte = stand(sitzungen = listOf(laufend(seitMinuten = 57))).zeitpunkte()

        assertEquals(listOf(Ausloeser.VORWARNUNG), zeitpunkte.faelligBis(clock.now()).map { it.ausloeser })
        assertEquals(
            listOf(Ausloeser.VORWARNUNG, Ausloeser.ALARM),
            zeitpunkte.faelligBis(clock.now() + minuten(3)).map { it.ausloeser },
        )
    }

    @Test
    fun `Die Vorwarnschwelle der Eltern gilt`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend()), vorwarnschwelle = minuten(10)).zeitpunkte()

        assertEquals(clock.now() + minuten(50), zeitpunkte.von(Ausloeser.VORWARNUNG))
    }

    @Test
    fun `Der Automatische Stopp kommt, wenn die Überziehung die Grenze erreicht`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 10))).zeitpunkte()

        // 50 min Restzeit plus 60 min Überziehungsgrenze.
        assertEquals(clock.now() + minuten(110), zeitpunkte.von(Ausloeser.AUTOMATISCHER_STOPP))
        assertEquals(clock.now() + minuten(110), zeitpunkte.automatischerStopp)
    }

    @Test
    fun `Ist die Grenze schon überschritten, steht der Stopp mit dem Zeitpunkt der Grenze an, nicht mit jetzt`() {
        // 90 min gelaufen bei 60 min Kontingent: die Überziehung (30 min) ist 30 min von der Grenze entfernt.
        val vorbei = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 90))).zeitpunkte()
        assertEquals(clock.now() + minuten(30), vorbei.von(Ausloeser.AUTOMATISCHER_STOPP))

        // 150 min gelaufen: die Grenze (nach 120 min) liegt 30 min zurück, die Sitzung hätte dort geendet.
        val lange = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 150))).zeitpunkte()
        assertEquals(clock.now() - minuten(30), lange.von(Ausloeser.AUTOMATISCHER_STOPP))
        assertEquals(listOf(Ausloeser.AUTOMATISCHER_STOPP), lange.faelligBis(clock.now()).map { it.ausloeser })
    }

    @Test
    fun `Nach dem Stopp klingelt nichts mehr, und der Stopp ist der letzte Zeitpunkt`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 10))).zeitpunkte()

        assertEquals(Ausloeser.AUTOMATISCHER_STOPP, zeitpunkte.liste.last().ausloeser)
        assertTrue(zeitpunkte.liste.all { it.zeitpunkt <= zeitpunkte.automatischerStopp!! })

        // 115 min gelaufen: der Stopp kommt in 5 min. Die Wiederholung in 2 min liegt davor, die nach 5 min nicht mehr.
        val laeuft = stand(kontingentMinuten = 60, sitzungen = listOf(laufend(seitMinuten = 115)))
        val vorDemStopp = laeuft.zeitpunkte(Alarmverlauf(clock.now() - minuten(55), clock.now() - minuten(3)))
        assertEquals(listOf(Ausloeser.ALARM, Ausloeser.AUTOMATISCHER_STOPP), vorDemStopp.liste.map { it.ausloeser })
        val amStopp = laeuft.zeitpunkte(Alarmverlauf(clock.now() - minuten(55), clock.now()))
        assertEquals(listOf(Ausloeser.AUTOMATISCHER_STOPP), amStopp.liste.map { it.ausloeser }, "Die Wiederholung käme nicht vor dem Stopp")
    }

    @Test
    fun `Die Überziehungsgrenze der Eltern gilt`() {
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(laufend()), ueberziehungsgrenze = minuten(15)).zeitpunkte()

        assertEquals(clock.now() + minuten(75), zeitpunkte.von(Ausloeser.AUTOMATISCHER_STOPP))
    }

    @Test
    fun `Lag die Überziehung schon vor dem Start über der Grenze, endet die Sitzung mit ihrem Start`() {
        val frueher = Sitzung("alt", clock.now() - minuten(300), SitzungsEnde(clock.now() - minuten(120), Endegrund.GESTOPPT))
        val zeitpunkte = stand(kontingentMinuten = 60, sitzungen = listOf(frueher, laufend(seitMinuten = 10, id = "neu"))).zeitpunkte()

        assertEquals(clock.now() - minuten(10), zeitpunkte.von(Ausloeser.AUTOMATISCHER_STOPP))
    }

    @Test
    fun `Ohne laufende Sitzung gibt es keinen Automatischen Stopp`() {
        assertNull(stand(sitzungen = emptyList()).zeitpunkte().automatischerStopp)
    }
}
