package io.github.drusin.kidscreentime.child

import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.api.SessionRecord
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Endegrund
import io.github.drusin.kidscreentime.ui.testChildSnapshot
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ticket 08: at the device, too, the earlier end wins (in time) when snapshot and Outbox disagree about a Sitzung. */
class ChildCalculationTest {
    private val start = Instant.parse("2026-01-05T08:00:00Z")
    private val serverEnd = start.plus(Duration.ofMinutes(120))
    private val snapshot = testChildSnapshot(
        serverTime = start.plus(Duration.ofMinutes(130)),
        sessions = listOf(SessionRecord("s1", start.toEpochMilli(), serverEnd.toEpochMilli(), SessionEndReason.AUTOMATIC_STOP)),
    )

    private fun endOf(vararg pending: PendingSession) =
        snapshot.toKindStand(pending.toList()).sitzungen.single().ende!!

    @Test
    fun `Ein früheres Ende aus der Outbox ersetzt das spätere Ende des Servers`() {
        val ende = endOf(PendingSession("s1", start.toEpochMilli(), start.plus(Duration.ofMinutes(50)).toEpochMilli()))

        assertEquals(start.plus(Duration.ofMinutes(50)), ende.zeitpunkt)
        assertEquals(Endegrund.GESTOPPT, ende.grund)
    }

    @Test
    fun `Ein späteres Ende aus der Outbox ändert das Ende des Servers nicht`() {
        val ende = endOf(PendingSession("s1", start.toEpochMilli(), start.plus(Duration.ofMinutes(125)).toEpochMilli()))

        assertEquals(serverEnd, ende.zeitpunkt)
        assertEquals(Endegrund.AUTOMATISCHER_STOPP, ende.grund)
    }
}
