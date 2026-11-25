package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ticket 16: Verlauf. Berlin im Januar 2026 (UTC+1): Mitternacht ist 23:00 UTC. Der 5. Januar 2026 ist ein Montag. */
class VerlaufTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(utc: String): Instant = Instant.parse(utc)

    private fun minuten(n: Long): Duration = Duration.ofMinutes(n)

    private fun stand(
        vorlage: Wochenvorlage = Wochenvorlage.von(WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), 60)),
        sitzungen: List<Sitzung> = emptyList(),
        anpassungen: List<Tagesanpassung> = emptyList(),
    ) = KindStand(zeitzone = berlin, wochenvorlage = vorlage, tagesanpassungen = anpassungen, sitzungen = sitzungen)

    private val jetzt = at("2026-01-07T12:00:00Z") // Mittwoch

    @Test
    fun `Der Verlauf listet die letzten Tage mit dem juengsten zuerst`() {
        val verlauf = stand().verlauf(jetzt, tage = 3)

        assertEquals(listOf(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 5)), verlauf.map { it.tag })
    }

    @Test
    fun `Ohne heute beginnt der Verlauf gestern`() {
        val verlauf = stand().verlauf(jetzt, tage = 2, mitHeute = false)

        assertEquals(listOf(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 5)), verlauf.map { it.tag })
    }

    @Test
    fun `Ein Tag ohne Sitzung steht mit Verbrauch 0 und seinem Kontingent drin`() {
        val tag = stand().verlauf(jetzt, tage = 2, mitHeute = false).first()

        assertEquals(Duration.ZERO, tag.verbrauch)
        assertEquals(minuten(60), tag.tageskontingent.gesamt)
        assertEquals(Duration.ZERO, tag.ueberziehung)
        assertTrue(tag.sitzungen.isEmpty())
        assertTrue(tag.anpassungen.isEmpty())
    }

    @Test
    fun `Verbrauch und Ueberziehung eines vergangenen Tages`() {
        val kind = stand(sitzungen = listOf(Sitzung("s1", at("2026-01-06T15:00:00Z"), SitzungsEnde(at("2026-01-06T16:15:00Z"), Endegrund.GESTOPPT))))

        val gestern = kind.verlauf(jetzt, tage = 2, mitHeute = false).first()

        assertEquals(minuten(75), gestern.verbrauch)
        assertEquals(minuten(15), gestern.ueberziehung)
    }

    @Test
    fun `Das Tageskontingent eines vergangenen Tages ist die Vorlage von 00 00 dieses Tages`() {
        val vorlage = Wochenvorlage.von(
            WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), 60),
            WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 6), 30),
        )

        val verlauf = stand(vorlage = vorlage).verlauf(jetzt, tage = 3)

        assertEquals(listOf(30L, 30L, 60L), verlauf.map { it.tageskontingent.vorlage.toMinutes() })
    }

    @Test
    fun `Das Tageskontingent eines vergangenen Tages enthaelt die Anpassungen dieses Tages und klemmt bei 0`() {
        val kind = stand(
            anpassungen = listOf(
                Tagesanpassung(LocalDate.of(2026, 1, 6), 15, at("2026-01-06T10:00:00Z")),
                Tagesanpassung(LocalDate.of(2026, 1, 5), -90, at("2026-01-05T10:00:00Z")),
            ),
        )

        val verlauf = kind.verlauf(jetzt, tage = 3)

        assertEquals(60L, verlauf[0].tageskontingent.gesamt.toMinutes())
        assertEquals(75L, verlauf[1].tageskontingent.gesamt.toMinutes())
        assertEquals(0L, verlauf[2].tageskontingent.gesamt.toMinutes())
        assertEquals(15L, verlauf[1].tageskontingent.anpassungen.toMinutes())
    }

    @Test
    fun `Eine Sitzung ueber Mitternacht steht an beiden Tagen mit ihrem Teil`() {
        // Montag 23:30 Berlin (22:30 UTC) bis Dienstag 00:20 Berlin (23:20 UTC am Montag).
        val kind = stand(sitzungen = listOf(Sitzung("s1", at("2026-01-05T22:30:00Z"), SitzungsEnde(at("2026-01-05T23:20:00Z"), Endegrund.GESTOPPT))))

        val verlauf = kind.verlauf(jetzt, tage = 3)
        val montag = verlauf[2]
        val dienstag = verlauf[1]

        assertEquals(minuten(30), montag.verbrauch)
        assertEquals(minuten(20), dienstag.verbrauch)
        val abend = montag.sitzungen.single()
        assertEquals(at("2026-01-05T22:30:00Z"), abend.von)
        assertEquals(at("2026-01-05T23:00:00Z"), abend.bis)
        assertTrue(abend.setztSichFort)
        assertFalse(abend.vomVortag)
        assertNull(abend.endegrund, "Der Endegrund gehört an den Tag, an dem die Sitzung endet")
        val nacht = dienstag.sitzungen.single()
        assertEquals(at("2026-01-05T23:00:00Z"), nacht.von)
        assertEquals(at("2026-01-05T23:20:00Z"), nacht.bis)
        assertTrue(nacht.vomVortag)
        assertFalse(nacht.setztSichFort)
        assertEquals(Endegrund.GESTOPPT, nacht.endegrund)
    }

    @Test
    fun `Die Aufteilung ueber Mitternacht stimmt mit dem Tagesstand ueberein`() {
        val kind = stand(sitzungen = listOf(Sitzung("s1", at("2026-01-05T22:30:00Z"), SitzungsEnde(at("2026-01-05T23:20:00Z"), Endegrund.GESTOPPT))))
        val verlauf = kind.verlauf(jetzt, tage = 3)

        assertEquals(kind.tagesstand(at("2026-01-05T22:59:59Z")).verbrauch.toMinutes() + 1, verlauf[2].verbrauch.toMinutes())
        assertEquals(kind.tagesstand(at("2026-01-06T10:00:00Z")).verbrauch, verlauf[1].verbrauch)
    }

    @Test
    fun `Ein Tag mit 25 Stunden rechnet in verstrichenen Minuten`() {
        // Sonntag 25. Oktober 2026 hat in Berlin 25 Stunden; Sitzung ueber die ganze Nacht von 22:00 bis 06:00 Berlin.
        val start = LocalDate.of(2026, 10, 24).atTime(22, 0).atZone(berlin).toInstant()
        val ende = LocalDate.of(2026, 10, 25).atTime(6, 0).atZone(berlin).toInstant()
        val kind = stand(
            vorlage = Wochenvorlage.von(WochenvorlageVersion.gleichbleibend(LocalDate.of(2026, 1, 1), 600)),
            sitzungen = listOf(Sitzung("s1", start, SitzungsEnde(ende, Endegrund.GESTOPPT))),
        )

        val verlauf = kind.verlauf(at("2026-10-26T12:00:00Z"), tage = 3)
        val samstag = verlauf.first { it.tag == LocalDate.of(2026, 10, 24) }
        val sonntag = verlauf.first { it.tag == LocalDate.of(2026, 10, 25) }

        assertEquals(minuten(120), samstag.verbrauch)
        assertEquals(minuten(7 * 60), sonntag.verbrauch)
    }

    @Test
    fun `Die Markierungen Automatisch gestoppt und offline erfasst stehen an der Sitzung`() {
        val kind = stand(
            sitzungen = listOf(
                Sitzung("a", at("2026-01-06T09:00:00Z"), SitzungsEnde(at("2026-01-06T10:00:00Z"), Endegrund.AUTOMATISCHER_STOPP)),
                Sitzung("b", at("2026-01-06T11:00:00Z"), SitzungsEnde(at("2026-01-06T11:10:00Z"), Endegrund.GESTOPPT), offlineErfasst = true),
            ),
        )

        val (a, b) = kind.verlauf(jetzt, tage = 2, mitHeute = false).first().sitzungen

        assertEquals(Endegrund.AUTOMATISCHER_STOPP, a.endegrund)
        assertFalse(a.offlineErfasst)
        assertEquals(Endegrund.GESTOPPT, b.endegrund)
        assertTrue(b.offlineErfasst)
    }

    @Test
    fun `Eine laufende Sitzung steht heute ohne Ende`() {
        val kind = stand(sitzungen = listOf(Sitzung("s1", at("2026-01-07T11:00:00Z"))))

        val heute = kind.verlauf(jetzt, tage = 1).single()
        val sitzung = heute.sitzungen.single()

        assertNull(sitzung.bis)
        assertNull(sitzung.endegrund)
        assertFalse(sitzung.setztSichFort)
        assertEquals(minuten(60), sitzung.dauer)
        assertEquals(minuten(60), heute.verbrauch)
    }

    @Test
    fun `Eine ueber Mitternacht laufende Sitzung setzt sich am Vortag fort`() {
        // Seit gestern 23:00 Berlin (22:00 UTC) läuft die Sitzung noch: gestern 60 min, heute ab 00:00 bis 13:00 Berlin.
        val kind = stand(sitzungen = listOf(Sitzung("s1", at("2026-01-06T22:00:00Z"))))

        val verlauf = kind.verlauf(jetzt, tage = 2)

        assertTrue(verlauf[1].sitzungen.single().setztSichFort)
        assertEquals(minuten(60), verlauf[1].verbrauch)
        assertNull(verlauf[0].sitzungen.single().bis)
        assertTrue(verlauf[0].sitzungen.single().vomVortag)
    }

    @Test
    fun `Eine Sitzung die genau um Mitternacht endet steht nur am alten Tag`() {
        val kind = stand(sitzungen = listOf(Sitzung("s1", at("2026-01-05T22:30:00Z"), SitzungsEnde(at("2026-01-05T23:00:00Z"), Endegrund.GESTOPPT))))

        val verlauf = kind.verlauf(jetzt, tage = 3)

        assertEquals(1, verlauf[2].sitzungen.size)
        assertFalse(verlauf[2].sitzungen.single().setztSichFort)
        assertTrue(verlauf[1].sitzungen.isEmpty())
    }

    @Test
    fun `Eine zurueckgenommene Anpassung ist markiert und ihre Gegenbuchung auch`() {
        val tag = LocalDate.of(2026, 1, 6)
        val kind = stand(
            anpassungen = listOf(
                Tagesanpassung(tag, 30, at("2026-01-06T10:00:00Z"), id = "1"),
                Tagesanpassung(tag, 10, at("2026-01-06T11:00:00Z"), id = "2"),
                Tagesanpassung(tag, -30, at("2026-01-06T12:00:00Z"), id = "3", gegenbuchungZu = "1"),
            ),
        )

        val gestern = kind.verlauf(jetzt, tage = 2, mitHeute = false).first()

        assertEquals(listOf(30, 10, -30), gestern.anpassungen.map { it.deltaMinuten })
        assertEquals(listOf(true, false, false), gestern.anpassungen.map { it.zurueckgenommen })
        assertEquals(listOf(false, false, true), gestern.anpassungen.map { it.istGegenbuchung })
        assertEquals(70L, gestern.tageskontingent.gesamt.toMinutes())
    }

    @Test
    fun `Buchungen nach jetzt bleiben unsichtbar`() {
        val kind = stand(anpassungen = listOf(Tagesanpassung(LocalDate.of(2026, 1, 7), 30, at("2026-01-07T13:00:00Z"))))

        val heute = kind.verlauf(jetzt, tage = 1).single()

        assertTrue(heute.anpassungen.isEmpty())
        assertEquals(60L, heute.tageskontingent.gesamt.toMinutes())
    }
}
