package io.github.drusin.kidscreentime.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingQrTest {
    @Test
    fun `der QR-Code traegt Server-URL und Kopplungscode und laesst sich wieder lesen`() {
        val raw = PairingQr.encode("https://kst.example.org:8443/familie?x=1&y=2", "ABCD-EFGH")

        assertEquals(PairingQr.Content("https://kst.example.org:8443/familie?x=1&y=2", "ABCD-EFGH"), PairingQr.parse(raw))
    }

    @Test
    fun `Umlaute in der Server-URL bleiben erhalten`() {
        val raw = PairingQr.encode("https://käse.example.org", "ABCD-EFGH")

        assertEquals("https://käse.example.org", PairingQr.parse(raw)?.serverUrl)
    }

    @Test
    fun `fremde oder unvollstaendige QR-Codes werden abgelehnt`() {
        assertNull(PairingQr.parse("https://example.org"))
        assertNull(PairingQr.parse("kidscreentime://pair?server=https%3A%2F%2Fx.org"))
        assertNull(PairingQr.parse("kidscreentime://pair?code=ABCD-EFGH"))
        assertNull(PairingQr.parse(""))
    }
}
