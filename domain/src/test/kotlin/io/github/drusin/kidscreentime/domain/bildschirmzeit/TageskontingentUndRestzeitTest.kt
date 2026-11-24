package io.github.drusin.kidscreentime.domain.bildschirmzeit

import io.github.drusin.kidscreentime.domain.clock.ManualClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TageskontingentUndRestzeitTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // Montag, 5. Januar 2026, 09:00 in Berlin.
    private val clock = ManualClock(Instant.parse("2026-01-05T08:00:00Z"))
    private val montag = LocalDate.of(2026, 1, 5)

    private fun minuten(n: Long): Duration = Duration.ofMinutes(n)

    private fun stand(
        vorlage: WochenvorlageVersion = WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), 60),
        weitereVersionen: List<WochenvorlageVersion> = emptyList(),
        anpassungen: List<Tagesanpassung> = emptyList(),
        sitzungen: List<Sitzung> = emptyList(),
        vorwarnschwelle: Duration = minuten(5),
        ueberziehungsgrenze: Duration = minuten(60),
    ) = KindStand(
        zeitzone = berlin,
        wochenvorlage = Wochenvorlage(listOf(vorlage) + weitereVersionen),
        tagesanpassungen = anpassungen,
        sitzungen = sitzungen,
        vorwarnschwelle = vorwarnschwelle,
        ueberziehungsgrenze = ueberziehungsgrenze,
    )

    private fun abgeschlossen(id: String, start: Instant, dauer: Duration) =
        Sitzung(id, start, SitzungsEnde(start + dauer, Endegrund.GESTOPPT))

    private fun anpassung(delta: Int, gebuchtAm: Instant = clock.now().minusSeconds(3600), tag: LocalDate = montag) =
        Tagesanpassung(tag, delta, gebuchtAm)

    // --- Tag und Tageskontingent ---

    @Test
    fun `der Tag richtet sich nach der Familien-Zeitzone und nicht nach UTC`() {
        val kurzNachMitternachtInBerlin = Instant.parse("2026-01-05T23:30:00Z") // 00:30 am Dienstag in Berlin
        val tagesstand = stand().tagesstand(kurzNachMitternachtInBerlin)
        assertEquals(LocalDate.of(2026, 1, 6), tagesstand.tag)
        assertEquals(Instant.parse("2026-01-06T23:00:00Z"), tagesstand.tagesende)
    }

    @Test
    fun `das Tageskontingent ist der Wert der Wochenvorlage fuer den Wochentag`() {
        val vorlage = WochenvorlageVersion.ausMontagBisSonntag(LocalDate.of(2026, 1, 1), 30, 40, 50, 60, 70, 120, 150)
        assertEquals(minuten(30), stand(vorlage).tagesstand(clock.now()).tageskontingent.gesamt) // Montag
        clock.advance(Duration.ofDays(5)) // Samstag
        assertEquals(minuten(120), stand(vorlage).tagesstand(clock.now()).tageskontingent.gesamt)
    }

    @Test
    fun `eine Vorlagen-Version mit gueltig ab morgen aendert das heutige Tageskontingent nicht`() {
        val abMorgen = WochenvorlageVersion.gleichbleibend(montag.plusDays(1), 90)
        val kind = stand(weitereVersionen = listOf(abMorgen))

        assertEquals(minuten(60), kind.tagesstand(clock.now()).tageskontingent.gesamt)
        clock.advance(Duration.ofDays(1))
        assertEquals(minuten(90), kind.tagesstand(clock.now()).tageskontingent.gesamt)
    }

    @Test
    fun `fuer vergangene Tage gilt die damals gueltige Vorlagen-Version`() {
        val neu = WochenvorlageVersion.gleichbleibend(montag, 90)
        val kind = stand(weitereVersionen = listOf(neu))

        val vorEinerWoche = clock.now().minus(Duration.ofDays(2))
        assertEquals(minuten(60), kind.tagesstand(vorEinerWoche).tageskontingent.gesamt)
        assertEquals(minuten(90), kind.tagesstand(clock.now()).tageskontingent.gesamt)
    }

    @Test
    fun `mehrere Tagesanpassungen addieren sich zum Tageskontingent`() {
        val kontingent = stand(anpassungen = listOf(anpassung(+30), anpassung(+15), anpassung(-5)))
            .tagesstand(clock.now()).tageskontingent

        assertEquals(minuten(60), kontingent.vorlage)
        assertEquals(minuten(40), kontingent.anpassungen)
        assertEquals(minuten(100), kontingent.gesamt)
    }

    @Test
    fun `eine Gegenbuchung hebt eine Tagesanpassung auf`() {
        val kontingent = stand(anpassungen = listOf(anpassung(+30), anpassung(-30)))
            .tagesstand(clock.now()).tageskontingent

        assertEquals(Duration.ZERO, kontingent.anpassungen)
        assertEquals(minuten(60), kontingent.gesamt)
    }

    @Test
    fun `Tagesanpassungen anderer Tage zaehlen nicht`() {
        val gestern = anpassung(+30, tag = montag.minusDays(1))
        val morgen = anpassung(+30, tag = montag.plusDays(1))
        assertEquals(minuten(60), stand(anpassungen = listOf(gestern, morgen)).tagesstand(clock.now()).tageskontingent.gesamt)
    }

    @Test
    fun `eine noch nicht gebuchte Tagesanpassung zaehlt zum Zeitpunkt vor der Buchung nicht`() {
        val kind = stand(anpassungen = listOf(anpassung(+30, gebuchtAm = clock.now().plusSeconds(600))))

        assertEquals(minuten(60), kind.tagesstand(clock.now()).tageskontingent.gesamt)
        clock.advance(Duration.ofMinutes(10))
        assertEquals(minuten(90), kind.tagesstand(clock.now()).tageskontingent.gesamt)
    }

    @Test
    fun `das Tageskontingent ist nie kleiner als 0`() {
        val kontingent = stand(anpassungen = listOf(anpassung(-90))).tagesstand(clock.now()).tageskontingent

        assertEquals(minuten(-30), kontingent.vorlage + kontingent.anpassungen)
        assertEquals(Duration.ZERO, kontingent.gesamt)
    }

    // --- Verbrauch, Restzeit, Ueberziehung ---

    @Test
    fun `ohne Sitzung ist nichts verbraucht und die Restzeit gleich dem Tageskontingent`() {
        val tagesstand = stand().tagesstand(clock.now())

        assertEquals(Duration.ZERO, tagesstand.verbrauch)
        assertEquals(minuten(60), tagesstand.restzeit)
        assertEquals(Duration.ZERO, tagesstand.ueberziehung)
    }

    @Test
    fun `abgeschlossene Sitzungen des Tages summieren sich zum Verbrauch`() {
        val erste = abgeschlossen("a", Instant.parse("2026-01-05T06:00:00Z"), minuten(10))
        val zweite = abgeschlossen("b", Instant.parse("2026-01-05T07:00:00Z"), minuten(15))
        val tagesstand = stand(sitzungen = listOf(erste, zweite)).tagesstand(clock.now())

        assertEquals(minuten(25), tagesstand.verbrauch)
        assertEquals(minuten(35), tagesstand.restzeit)
    }

    @Test
    fun `eine laufende Sitzung zaehlt bis jetzt und waechst mit der verstrichenen Zeit`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())))

        assertEquals(Duration.ZERO, kind.tagesstand(clock.now()).verbrauch)
        clock.advance(minuten(20))
        val tagesstand = kind.tagesstand(clock.now())
        assertEquals(minuten(20), tagesstand.verbrauch)
        assertEquals(minuten(40), tagesstand.restzeit)
    }

    @Test
    fun `Sekunden zaehlen als verstrichene Zeit mit`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())))
        clock.advance(Duration.ofSeconds(90))

        assertEquals(Duration.ofSeconds(90), kind.tagesstand(clock.now()).verbrauch)
        assertEquals(Duration.ofSeconds(60 * 60 - 90), kind.tagesstand(clock.now()).restzeit)
    }

    @Test
    fun `Sitzungen vergangener Tage zaehlen nicht zum heutigen Verbrauch`() {
        val gestern = abgeschlossen("a", Instant.parse("2026-01-04T10:00:00Z"), minuten(45))

        assertEquals(Duration.ZERO, stand(sitzungen = listOf(gestern)).tagesstand(clock.now()).verbrauch)
    }

    @Test
    fun `von einer Sitzung ueber Mitternacht zaehlt nur der Teil im heutigen Tag`() {
        // Gestern 23:50 Berlin bis heute 00:20 Berlin: 10 Minuten gestern, 20 heute.
        val start = Instant.parse("2026-01-04T22:50:00Z")
        val ueberMitternacht = abgeschlossen("a", start, minuten(30))
        val kind = stand(sitzungen = listOf(ueberMitternacht))

        // Heute (Dienstag, 00:20 Berlin = 23:20Z am Vortag) zählen 20 Minuten.
        assertEquals(minuten(20), kind.tagesstand(Instant.parse("2026-01-04T23:20:00Z")).verbrauch)
        // Gestern, 23:55 Berlin, waren es 5 Minuten.
        assertEquals(minuten(5), kind.tagesstand(Instant.parse("2026-01-04T22:55:00Z")).verbrauch)
    }

    @Test
    fun `eine Sitzung, die nach dem Zeitpunkt der Abfrage beginnt, zaehlt noch nicht`() {
        val spaeter = Sitzung("a", clock.now().plusSeconds(600))

        assertEquals(Duration.ZERO, stand(sitzungen = listOf(spaeter)).tagesstand(clock.now()).verbrauch)
    }

    @Test
    fun `ueber das Tageskontingent hinaus wird die Restzeit 0 und die Ueberziehung zaehlt`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())))
        clock.advance(minuten(75))
        val tagesstand = kind.tagesstand(clock.now())

        assertEquals(minuten(75), tagesstand.verbrauch)
        assertEquals(Duration.ZERO, tagesstand.restzeit)
        assertEquals(minuten(15), tagesstand.ueberziehung)
    }

    @Test
    fun `eine Tagesanpassung nach der Ueberziehung bringt die Restzeit wieder ueber 0`() {
        val verbraucht = abgeschlossen("a", Instant.parse("2026-01-05T06:00:00Z"), minuten(70))
        val ohneBonus = stand(sitzungen = listOf(verbraucht))
        val mitBonus = stand(sitzungen = listOf(verbraucht), anpassungen = listOf(anpassung(+30)))

        assertEquals(minuten(10), ohneBonus.tagesstand(clock.now()).ueberziehung)
        assertEquals(minuten(20), mitBonus.tagesstand(clock.now()).restzeit)
        assertEquals(Duration.ZERO, mitBonus.tagesstand(clock.now()).ueberziehung)
    }

    @Test
    fun `an einem Tag mit Zeitumstellung zaehlen tatsaechlich verstrichene Minuten`() {
        // 29. März 2026: Sommerzeit beginnt um 02:00, der Tag hat 23 Stunden.
        val start = Instant.parse("2026-03-29T00:30:00Z") // 01:30 MEZ
        val ueberUmstellung = abgeschlossen("a", start, minuten(60)) // endet 03:30 MESZ
        val tagesstand = stand(sitzungen = listOf(ueberUmstellung)).tagesstand(Instant.parse("2026-03-29T12:00:00Z"))

        assertEquals(minuten(60), tagesstand.verbrauch)
        assertEquals(Duration.ofHours(23), Duration.between(Instant.parse("2026-03-28T23:00:00Z"), tagesstand.tagesende))
    }

    // --- Start erlaubt ---

    @Test
    fun `ein Start ist bei Restzeit ueber 0 erlaubt`() {
        assertTrue(stand().tagesstand(clock.now()).startErlaubt)
    }

    @Test
    fun `ein Start ist bei Restzeit 0 nicht erlaubt`() {
        val verbraucht = abgeschlossen("a", Instant.parse("2026-01-05T06:00:00Z"), minuten(60))

        assertFalse(stand(sitzungen = listOf(verbraucht)).tagesstand(clock.now()).startErlaubt)
    }

    @Test
    fun `ein Start ist bei einem Tageskontingent von 0 nicht erlaubt`() {
        assertFalse(stand(WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), 0)).tagesstand(clock.now()).startErlaubt)
    }

    @Test
    fun `waehrend eine Sitzung laeuft ist kein weiterer Start erlaubt`() {
        assertFalse(stand(sitzungen = listOf(Sitzung("a", clock.now()))).tagesstand(clock.now()).startErlaubt)
    }

    // --- Zustand ---

    @Test
    fun `ohne laufende Sitzung und mit Restzeit ist das Kind bereit`() {
        assertEquals(Zustand.BEREIT, stand().tagesstand(clock.now()).zustand)
    }

    @Test
    fun `bei laufender Sitzung ueber der Vorwarnschwelle laeuft das Kind`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())))
        clock.advance(minuten(30))

        assertEquals(Zustand.LAEUFT, kind.tagesstand(clock.now()).zustand)
    }

    @Test
    fun `genau bei der Vorwarnschwelle beginnt die Vorwarnung`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())), vorwarnschwelle = minuten(5))

        clock.advance(minuten(54) + Duration.ofSeconds(59))
        assertEquals(Zustand.LAEUFT, kind.tagesstand(clock.now()).zustand)
        clock.advance(Duration.ofSeconds(1))
        assertEquals(Zustand.VORWARNUNG, kind.tagesstand(clock.now()).zustand)
    }

    @Test
    fun `die Vorwarnung richtet sich nach der eingestellten Schwelle`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())), vorwarnschwelle = minuten(15))
        clock.advance(minuten(46))

        assertEquals(Zustand.VORWARNUNG, kind.tagesstand(clock.now()).zustand)
    }

    @Test
    fun `liegt die Restzeit beim Start schon unter der Schwelle, ist sofort Vorwarnung`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())), vorwarnschwelle = minuten(90))

        assertEquals(Zustand.VORWARNUNG, kind.tagesstand(clock.now()).zustand)
    }

    @Test
    fun `bei laufender Sitzung und Restzeit 0 ist das Kind in der Ueberziehung`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())))
        clock.advance(minuten(60))

        assertEquals(Zustand.UEBERZIEHUNG, kind.tagesstand(clock.now()).zustand)
        clock.advance(minuten(10))
        assertEquals(Zustand.UEBERZIEHUNG, kind.tagesstand(clock.now()).zustand)
    }

    @Test
    fun `ohne laufende Sitzung und ohne Restzeit ist die Zeit aufgebraucht`() {
        val verbraucht = abgeschlossen("a", Instant.parse("2026-01-05T06:00:00Z"), minuten(65))

        assertEquals(Zustand.AUFGEBRAUCHT, stand(sitzungen = listOf(verbraucht)).tagesstand(clock.now()).zustand)
    }

    @Test
    fun `eine Tagesanpassung holt eine laufende Sitzung aus der Ueberziehung zurueck in die Vorwarnung`() {
        val kind = stand(sitzungen = listOf(Sitzung("a", clock.now())))
        clock.advance(minuten(62))
        assertEquals(Zustand.UEBERZIEHUNG, kind.tagesstand(clock.now()).zustand)

        val mitBonus = kind.copy(tagesanpassungen = listOf(anpassung(+5, gebuchtAm = clock.now())))
        assertEquals(Zustand.VORWARNUNG, mitBonus.tagesstand(clock.now()).zustand)
    }

    @Test
    fun `der Tagesstand nennt die laufende Sitzung`() {
        val laufend = Sitzung("lauf", clock.now())
        val beendet = abgeschlossen("alt", Instant.parse("2026-01-05T06:00:00Z"), minuten(5))
        val tagesstand = stand(sitzungen = listOf(beendet, laufend)).tagesstand(clock.now())

        assertEquals(laufend, tagesstand.laufendeSitzung)
        assertEquals(null, stand(sitzungen = listOf(beendet)).tagesstand(clock.now()).laufendeSitzung)
    }
}
