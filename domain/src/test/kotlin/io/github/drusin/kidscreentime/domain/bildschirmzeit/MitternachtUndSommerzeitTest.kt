package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ticket 11: Mitternacht und Sommerzeit. Berlin 2026: Zeitumstellung am 29. März (23-h-Tag) und am 25. Oktober (25-h-Tag). */
class MitternachtUndSommerzeitTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun minuten(n: Long): Duration = Duration.ofMinutes(n)

    private fun at(utc: String): Instant = Instant.parse(utc)

    private fun stand(
        montag: Int = 60,
        dienstag: Int = 60,
        sonstige: Int = 60,
        sitzungen: List<Sitzung> = emptyList(),
        ueberziehungsgrenze: Duration = minuten(60),
        zeitzone: ZoneId = berlin,
    ) = KindStand(
        zeitzone = zeitzone,
        wochenvorlage = Wochenvorlage(
            listOf(
                WochenvorlageVersion(
                    LocalDate.of(2026, 1, 1),
                    DayOfWeek.entries.associateWith {
                        when (it) {
                            DayOfWeek.MONDAY -> montag
                            DayOfWeek.TUESDAY -> dienstag
                            else -> sonstige
                        }
                    },
                ),
            ),
        ),
        sitzungen = sitzungen,
        ueberziehungsgrenze = ueberziehungsgrenze,
    )

    private fun FaelligeZeitpunkte.von(ausloeser: Ausloeser) = liste.firstOrNull { it.ausloeser == ausloeser }?.zeitpunkt

    // Montag, 5. Januar 2026, 23:30 in Berlin (22:30 UTC); Mitternacht ist 23:00 UTC.
    private val montag2330 = at("2026-01-05T22:30:00Z")
    private val mitternacht = at("2026-01-05T23:00:00Z")

    @Test
    fun `Eine Sitzung ueber Mitternacht zaehlt bis 00 00 gegen den alten Tag und danach gegen den neuen`() {
        val kind = stand(montag = 120, dienstag = 90, sitzungen = listOf(Sitzung("s1", montag2330)))

        val vorMitternacht = kind.tagesstand(mitternacht - Duration.ofSeconds(1))
        assertEquals(LocalDate.of(2026, 1, 5), vorMitternacht.tag)
        assertEquals(Duration.ofMinutes(30).minusSeconds(1), vorMitternacht.verbrauch)

        val nachMitternacht = kind.tagesstand(mitternacht + minuten(30))
        assertEquals(LocalDate.of(2026, 1, 6), nachMitternacht.tag)
        assertEquals(minuten(30), nachMitternacht.verbrauch, "ab 00:00 zählt nur der Teil im neuen Tag")
        assertEquals(minuten(60), nachMitternacht.restzeit, "Dienstag 90 min minus 30 min")
    }

    @Test
    fun `Eine beendete Sitzung ueber Mitternacht wird beim Lesen auf beide Tage aufgeteilt`() {
        val sitzung = Sitzung("s1", montag2330, SitzungsEnde(mitternacht + minuten(15), Endegrund.GESTOPPT))
        val kind = stand(sitzungen = listOf(sitzung))

        assertEquals(minuten(30) - Duration.ofSeconds(1), kind.tagesstand(mitternacht - Duration.ofSeconds(1)).verbrauch, "bis 00:00 zählt der Teil im alten Tag")
        assertEquals(minuten(15), kind.tagesstand(mitternacht + minuten(40)).verbrauch, "danach nur der Teil im neuen Tag")
    }

    @Test
    fun `Mitternacht steht als faelliger Zeitpunkt an, solange die Sitzung ueber sie hinaus laeuft`() {
        val kind = stand(montag = 600, sitzungen = listOf(Sitzung("s1", montag2330)))

        val zeitpunkte = kind.faelligeZeitpunkte(montag2330)

        assertEquals(mitternacht, zeitpunkte.von(Ausloeser.MITTERNACHT))
        assertTrue(zeitpunkte.liste.none { it.zeitpunkt.isAfter(mitternacht) }, "nichts hinter dem Tagesende")
        assertEquals(zeitpunkte.liste.sortedBy { it.zeitpunkt }, zeitpunkte.liste)
    }

    @Test
    fun `Mitternacht steht nicht an ohne laufende Sitzung oder wenn der Automatische Stopp davor liegt`() {
        assertNull(stand().faelligeZeitpunkte(montag2330).von(Ausloeser.MITTERNACHT), "keine Sitzung")

        // Kontingent 60, schon 60 min Überziehung vor 23:30: Stopp um 23:30 liegt vor Mitternacht.
        val laeuftSchonLange = stand(sitzungen = listOf(Sitzung("s1", montag2330 - minuten(120))))
        val zeitpunkte = laeuftSchonLange.faelligeZeitpunkte(montag2330)
        assertNull(zeitpunkte.von(Ausloeser.MITTERNACHT))
        assertNotNull(zeitpunkte.von(Ausloeser.AUTOMATISCHER_STOPP))
    }

    @Test
    fun `Nach Mitternacht steht die naechste Mitternacht an, die alte ist erledigt`() {
        val kind = stand(montag = 600, dienstag = 24 * 60, sitzungen = listOf(Sitzung("s1", montag2330)))

        val zeitpunkte = kind.faelligeZeitpunkte(mitternacht + Duration.ofSeconds(1))

        assertEquals(mitternacht + Duration.ofHours(24), zeitpunkte.von(Ausloeser.MITTERNACHT))
    }

    @Test
    fun `Ein klingender Alarm verstummt um 00 00, wenn das neue Tageskontingent Restzeit laesst`() {
        // Montag 60 min, Sitzung seit 22:00: um 23:30 sind 30 min überzogen, der Alarm klingelt.
        val start = at("2026-01-05T21:00:00Z")
        val kind = stand(montag = 60, dienstag = 60, ueberziehungsgrenze = minuten(120), sitzungen = listOf(Sitzung("s1", start)))
        val verlauf = Alarmverlauf(vorwarnungGegeben = true, letzterAlarm = montag2330)

        // Der Alarm klingelt alle 5 min: um 23:35 ist die Wiederholung fällig.
        val klingeln = montag2330 + minuten(5)
        assertEquals(Zustand.UEBERZIEHUNG, kind.tagesstand(klingeln).zustand)
        assertEquals(klingeln, kind.faelligeZeitpunkte(klingeln, verlauf).von(Ausloeser.ALARM), "Alarm ist fällig")
        assertEquals(mitternacht, kind.faelligeZeitpunkte(klingeln, verlauf).von(Ausloeser.MITTERNACHT))

        val nachMitternacht = mitternacht + Duration.ofSeconds(1)
        val stand = kind.tagesstand(nachMitternacht)
        assertTrue(stand.restzeit.isPositive, "neues Kontingent: Restzeit > 0, ein klingender Alarm endet")
        assertEquals(Zustand.LAEUFT, stand.zustand)
        val neu = kind.faelligeZeitpunkte(nachMitternacht, verlauf)
        assertEquals(mitternacht + minuten(60), neu.von(Ausloeser.ALARM), "der nächste Alarm kommt, wenn das neue Kontingent verbraucht ist")
    }

    @Test
    fun `Bleibt das neue Tageskontingent bei 0, klingelt der Alarm nach Mitternacht sofort wieder`() {
        val start = at("2026-01-05T21:00:00Z")
        val kind = stand(montag = 60, dienstag = 0, ueberziehungsgrenze = minuten(120), sitzungen = listOf(Sitzung("s1", start)))
        val verlauf = Alarmverlauf(vorwarnungGegeben = true, letzterAlarm = montag2330)

        val nachMitternacht = mitternacht + Duration.ofSeconds(1)

        assertEquals(Zustand.UEBERZIEHUNG, kind.tagesstand(nachMitternacht).zustand)
        assertEquals(nachMitternacht, kind.faelligeZeitpunkte(nachMitternacht, verlauf).von(Ausloeser.ALARM))
    }

    @Test
    fun `Der Automatische Stopp rechnet nach Mitternacht mit dem neuen Tag`() {
        // Alter Tag: Stopp läge um 01:00. Neuer Tag (60 min + 60 min Grenze) ab 00:00: Stopp um 02:00.
        val start = at("2026-01-05T21:00:00Z")
        val kind = stand(montag = 60, dienstag = 60, sitzungen = listOf(Sitzung("s1", start)))

        assertEquals(at("2026-01-06T01:00:00Z"), kind.faelligeZeitpunkte(mitternacht + minuten(30)).automatischerStopp)
    }

    @Test
    fun `Ein Tag mit Sommerzeitbeginn hat 23 Stunden`() {
        // Sonntag, 29. März 2026: 00:00 CET = 28.3. 23:00 UTC, nächste Mitternacht = 29.3. 22:00 UTC (CEST).
        val kind = stand(sonstige = 24 * 60, sitzungen = listOf(Sitzung("s1", at("2026-03-28T23:00:00Z"))))
        val jetzt = at("2026-03-29T21:59:00Z")

        val tag = kind.tagesstand(jetzt)

        assertEquals(LocalDate.of(2026, 3, 29), tag.tag)
        assertEquals(at("2026-03-29T22:00:00Z"), tag.tagesende)
        assertEquals(minuten(22 * 60 + 59), tag.verbrauch, "tatsächlich verstrichene Minuten, nicht 23:59 auf der Wanduhr")
        assertEquals(minuten(61), tag.restzeit)
        assertEquals(at("2026-03-29T22:00:00Z"), kind.faelligeZeitpunkte(jetzt).von(Ausloeser.MITTERNACHT))
    }

    @Test
    fun `Ein Tag mit Sommerzeitende hat 25 Stunden`() {
        // Sonntag, 25. Oktober 2026: 00:00 CEST = 24.10. 22:00 UTC, nächste Mitternacht = 25.10. 23:00 UTC (CET).
        val kind = stand(sonstige = 25 * 60, sitzungen = listOf(Sitzung("s1", at("2026-10-24T22:00:00Z"))))
        val jetzt = at("2026-10-25T22:59:00Z")

        val tag = kind.tagesstand(jetzt)

        assertEquals(LocalDate.of(2026, 10, 25), tag.tag)
        assertEquals(at("2026-10-25T23:00:00Z"), tag.tagesende)
        assertEquals(minuten(24 * 60 + 59), tag.verbrauch)
        assertEquals(minuten(1), tag.restzeit)
        assertEquals(at("2026-10-25T23:00:00Z"), kind.faelligeZeitpunkte(jetzt).von(Ausloeser.MITTERNACHT))
    }

    @Test
    fun `Eine Sitzung ueber den Beginn der Sommerzeit zaehlt 60 Minuten, auch wenn die Wanduhr drei Stunden zeigt`() {
        // Umstellung 29.3. um 01:00 UTC (02:00 -> 03:00). Wanduhr: 01:30 CET bis 04:30 CEST.
        val sitzung = Sitzung("s1", at("2026-03-29T00:30:00Z"), SitzungsEnde(at("2026-03-29T01:30:00Z"), Endegrund.GESTOPPT))
        val kind = stand(sitzungen = listOf(sitzung))

        assertEquals(minuten(60), kind.tagesstand(at("2026-03-29T06:00:00Z")).verbrauch)
    }

    @Test
    fun `Eine Sitzung ueber das Ende der Sommerzeit zaehlt 60 Minuten, auch wenn die Wanduhr gleich bleibt`() {
        // Umstellung 25.10. um 01:00 UTC (03:00 -> 02:00). Wanduhr: 02:30 CEST bis 02:30 CET.
        val sitzung = Sitzung("s1", at("2026-10-25T00:30:00Z"), SitzungsEnde(at("2026-10-25T01:30:00Z"), Endegrund.GESTOPPT))
        val kind = stand(sitzungen = listOf(sitzung))

        assertEquals(minuten(60), kind.tagesstand(at("2026-10-25T06:00:00Z")).verbrauch)
    }

    @Test
    fun `Eine laufende Sitzung ueber die Umstellung rechnet Restzeit und Alarm in verstrichenen Minuten`() {
        // Start 29.3. 00:30 UTC, Kontingent 120 min: Alarm nach 120 echten Minuten = 02:30 UTC (04:30 CEST auf der Wanduhr).
        val kind = stand(sonstige = 120, sitzungen = listOf(Sitzung("s1", at("2026-03-29T00:30:00Z"))))

        val zeitpunkte = kind.faelligeZeitpunkte(at("2026-03-29T01:30:00Z"))

        assertEquals(at("2026-03-29T02:30:00Z"), zeitpunkte.von(Ausloeser.ALARM))
        assertEquals(minuten(60), kind.tagesstand(at("2026-03-29T01:30:00Z")).restzeit)
    }

    @Test
    fun `Die Familien-Zeitzone gilt unabhaengig von der Standardzeitzone der JVM`() {
        val kind = stand(montag = 120, dienstag = 90, sitzungen = listOf(Sitzung("s1", montag2330)))
        val erwartet = ausgerechnet(kind)

        for (id in listOf("UTC", "Pacific/Kiritimati", "America/Los_Angeles", "Asia/Kolkata")) {
            val vorher = TimeZone.getDefault()
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(erwartet, ausgerechnet(kind), "abweichende JVM-Zeitzone $id")
            } finally {
                TimeZone.setDefault(vorher)
            }
        }
    }

    private fun ausgerechnet(kind: KindStand): List<Any> {
        val nachMitternacht = kind.tagesstand(mitternacht + minuten(30))
        return listOf(
            nachMitternacht.tag,
            nachMitternacht.tagesende,
            nachMitternacht.verbrauch,
            nachMitternacht.restzeit,
            kind.faelligeZeitpunkte(montag2330),
        )
    }
}
