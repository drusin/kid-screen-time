package io.github.drusin.kidscreentime.domain.bildschirmzeit

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StandardwerteFuerEinNeuesKindTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val ersterTag = LocalDate.of(2026, 1, 5) // Montag

    private val neuesKind = KindStand(berlin, Wochenvorlage.von(WochenvorlageVersion.standard(ersterTag)))

    @Test
    fun `ein neues Kind hat an Werktagen 60 und am Wochenende 120 Minuten`() {
        val montag = neuesKind.tagesstand(Instant.parse("2026-01-05T08:00:00Z"))
        val samstag = neuesKind.tagesstand(Instant.parse("2026-01-10T08:00:00Z"))
        val sonntag = neuesKind.tagesstand(Instant.parse("2026-01-11T08:00:00Z"))

        assertEquals(Duration.ofMinutes(60), montag.tageskontingent.gesamt)
        assertEquals(Duration.ofMinutes(120), samstag.tageskontingent.gesamt)
        assertEquals(Duration.ofMinutes(120), sonntag.tageskontingent.gesamt)
    }

    @Test
    fun `ein neues Kind hat 5 Minuten Vorwarnschwelle und 60 Minuten Ueberziehungsgrenze`() {
        assertEquals(Duration.ofMinutes(5), neuesKind.vorwarnschwelle)
        assertEquals(Duration.ofMinutes(60), neuesKind.ueberziehungsgrenze)
    }

    @Test
    fun `die Version einer Wochenvorlage ist ab ihrem Datum gueltig und davor unbekannt`() {
        val vorlage = Wochenvorlage.von(WochenvorlageVersion.standard(ersterTag))

        assertEquals(null, vorlage.version(ersterTag.minusDays(1)))
        assertEquals(ersterTag, vorlage.version(ersterTag.plusDays(30))?.gueltigAb)
    }
}
