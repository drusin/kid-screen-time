package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.github.drusin.kidscreentime.api.ChildSyncRequest
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.RedeemPairingCodeResponse
import io.github.drusin.kidscreentime.api.SessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.TimeZone
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/**
 * Ticket 11: midnight at the server. The family zone is Europe/Berlin (Wochenvorlage 60 min every day, Überziehungsgrenze
 * 60 min); the clock starts Monday 2026-01-05 09:00 Berlin. Time is moved, then `scheduler.runDue()` runs what is due.
 */
class MidnightApiTest {
    @TempDir
    lateinit var dir: Path

    private suspend fun HttpClient.startSession(device: RedeemPairingCodeResponse): String {
        val id = UUID.randomUUID().toString()
        post(TestServer.PUBLIC + ApiPaths.CHILD_SYNC) {
            header("Authorization", "Bearer ${device.deviceToken}")
            contentType(ContentType.Application.Json)
            setBody(ChildSyncRequest(listOf(SessionUpdate(id, startAgeMillis = 0))))
        }
        return id
    }

    private fun TestServer.endOf(id: String): Long? = services.database.transaction { c ->
        c.prepareStatement("SELECT end_at FROM sessions WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs -> check(rs.next()); rs.getLong(1).takeUnless { rs.wasNull() } }
        }
    }

    /** Starts a Sitzung at [startUtc] (a time at the end of a day) and returns the server and its next due time. */
    private fun midnightFrom(startUtc: String): Instant? {
        var due: Instant? = null
        testApplication {
            val server = TestServer(dir.resolve(UUID.randomUUID().toString()).also { it.toFile().mkdirs() })
            server.install(this)
            val client = server.client(this)
            server.clock.setWallClock(Instant.parse(startUtc))
            val child = server.pairDevice(client, DeviceRole.CHILD)
            client.startSession(child)
            due = server.services.scheduler.nextDue()
        }
        return due
    }

    @Test
    fun `Der Planer wacht um Mitternacht auf, wenn die Sitzung darüber hinaus läuft, und rechnet mit dem neuen Tag`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)
        val child = server.pairDevice(client, DeviceRole.CHILD)
        server.clock.advance(Duration.ofHours(13) + Duration.ofMinutes(30)) // Montag 22:30
        val id = client.startSession(child)
        val midnight = Instant.parse("2026-01-05T23:00:00Z")

        // Alter Tag: Montag 60 min, Überziehungsgrenze 60 min: der Stopp läge um 00:30, hinter Mitternacht. Der Planer wacht davor auf.
        assertEquals(midnight, server.services.scheduler.nextDue())

        server.clock.advance(Duration.ofMinutes(90)) // 00:00 Dienstag
        assertEquals(0, server.services.scheduler.runDue())
        // Neuer Tag: Dienstag 60 min ab 00:00 plus 60 min Grenze = Stopp um 02:00, nicht um 00:30.
        assertEquals(Instant.parse("2026-01-06T01:00:00Z"), server.services.scheduler.nextDue())

        server.clock.advance(Duration.ofMinutes(90)) // 01:30, der Stopp des alten Tages (00:30) ist verstrichen
        assertEquals(0, server.services.scheduler.runDue())
        assertNull(server.endOf(id), "Die Sitzung läuft weiter: ab 00:00 zählt das neue Kontingent")

        server.clock.advance(Duration.ofMinutes(35)) // 02:05
        assertEquals(1, server.services.scheduler.runDue())
        assertEquals(Instant.parse("2026-01-06T01:00:00Z").toEpochMilli(), server.endOf(id))
    }

    @Test
    fun `Ohne laufende Sitzung steht keine Mitternacht an`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        server.pairDevice(server.client(this), DeviceRole.CHILD)

        assertNull(server.services.scheduler.nextDue())
    }

    @Test
    fun `Mitternacht ist am Tag der Zeitumstellung nach 23 bzw 25 Stunden`() {
        // 29.3.2026 (Beginn der Sommerzeit): 22:30 Berlin = 20:30 UTC, Mitternacht = 22:00 UTC.
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), midnightFrom("2026-03-29T20:30:00Z"))
        // 25.10.2026 (Ende der Sommerzeit): 22:30 Berlin = 21:30 UTC, Mitternacht = 23:00 UTC.
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), midnightFrom("2026-10-25T21:30:00Z"))
    }

    @Test
    fun `Mitternacht gilt in der Familien-Zeitzone, auch wenn die JVM eine andere Zeitzone hat`() {
        val vorher = TimeZone.getDefault()
        try {
            for (id in listOf("UTC", "Pacific/Kiritimati", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(Instant.parse("2026-01-05T23:00:00Z"), midnightFrom("2026-01-05T21:30:00Z"), "JVM-Zeitzone $id")
            }
        } finally {
            TimeZone.setDefault(vorher)
        }
    }
}
