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
    ) = KindStand(
        zeitzone = berlin,
        wochenvorlage = Wochenvorlage(listOf(WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), kontingentMinuten))),
        tagesanpassungen = anpassungen,
        sitzungen = sitzungen,
        vorwarnschwelle = vorwarnschwelle,
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
    fun `Die Vorwarnung kommt höchstens einmal pro Sitzung`() {
        val gegeben = Alarmverlauf(vorwarnungGegeben = true)

        val zeitpunkte = stand(sitzungen = listOf(laufend(seitMinuten = 57))).zeitpunkte(gegeben)

        assertNull(zeitpunkte.von(Ausloeser.VORWARNUNG))
        assertEquals(clock.now() + minuten(3), zeitpunkte.von(Ausloeser.ALARM))
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
        val geklingeltVor2Min = Alarmverlauf(vorwarnungGegeben = true, letzterAlarm = clock.now() - minuten(2))

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
            .zeitpunkte(Alarmverlauf(vorwarnungGegeben = true, letzterAlarm = clock.now() - minuten(1)))

        assertEquals(clock.now() + minuten(15), zeitpunkte.von(Ausloeser.ALARM))
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

        assertTrue(zeitpunkte.liste.isEmpty())
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
}
