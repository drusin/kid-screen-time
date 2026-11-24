package io.github.drusin.kidscreentime.server

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerConfigTest {
    private val valid = mapOf(
        "ADMIN_PASSWORD" to "pw",
        "FAMILY_TIME_ZONE" to "Europe/Berlin",
        "PUBLIC_SERVER_URL" to "https://kst.example.org/",
        "NTFY_PUBLIC_URL" to "https://ntfy.example.org",
        "NTFY_INTERNAL_URL" to "http://ntfy:80",
    )

    @Test
    fun `Vollständige Konfiguration wird gelesen und bekommt Standardports`() {
        val config = ServerConfig.fromEnvironment(valid)

        assertEquals(ZoneId.of("Europe/Berlin"), config.familyZone)
        assertEquals("https://kst.example.org", config.publicServerUrl)
        assertEquals(8080, config.port)
        assertEquals(8081, config.adminPort)
    }

    @Test
    fun `Jeder fehlende Pflichtwert verhindert den Start und wird genannt`() {
        listOf("ADMIN_PASSWORD", "FAMILY_TIME_ZONE", "PUBLIC_SERVER_URL", "NTFY_PUBLIC_URL", "NTFY_INTERNAL_URL").forEach { name ->
            val e = assertFailsWith<ConfigurationException>(name) { ServerConfig.fromEnvironment(valid - name) }
            assertTrue(e.message!!.contains("$name fehlt"), e.message)
        }
    }

    @Test
    fun `Leere Pflichtwerte zählen als fehlend`() {
        assertFailsWith<ConfigurationException> { ServerConfig.fromEnvironment(valid + ("ADMIN_PASSWORD" to "  ")) }
    }

    @Test
    fun `Unbekannte Zeitzone, ungültige URL und gleiche Ports werden abgelehnt`() {
        assertFailsWith<ConfigurationException> { ServerConfig.fromEnvironment(valid + ("FAMILY_TIME_ZONE" to "Mars/Olympus")) }
        assertFailsWith<ConfigurationException> { ServerConfig.fromEnvironment(valid + ("PUBLIC_SERVER_URL" to "kst.example.org")) }
        assertFailsWith<ConfigurationException> { ServerConfig.fromEnvironment(valid + ("ADMIN_PORT" to "8080")) }
    }
}
