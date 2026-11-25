package io.github.drusin.kidscreentime.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Ausloeser
import io.github.drusin.kidscreentime.sync.SyncScenario
import io.github.drusin.kidscreentime.sync.syncScenario
import java.time.Duration
import java.time.Instant
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Ticket 11: the Alarmkern plans again at midnight (Familien-Zeitzone). The scenario clock starts Monday 2026-01-05
 * 09:00 in Berlin; midnight is Tuesday 00:00 Berlin = 2026-01-05 23:00 UTC.
 */
@RunWith(RobolectricTestRunner::class)
class MitternachtAlarmTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val effects = RecordingAlarmEffects()
    private val midnight = Instant.parse("2026-01-05T23:00:00Z")
    private val minute = Duration.ofMinutes(1)

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice(alarmEffects = effects)
        sync()
        block()
    }

    private fun planned(): Instant? =
        shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()?.let { Instant.ofEpochMilli(it.triggerAtMs) }

    private fun SyncScenario.kern() = child.app.container.alarmKern

    /** Monday 22:30 Berlin; the Sitzung then runs over midnight. */
    private suspend fun SyncScenario.startAt2230(): String {
        advance(Duration.ofHours(13) + Duration.ofMinutes(30))
        return start()
    }

    @Test
    fun `Läuft die Sitzung über Mitternacht, plant der Alarmkern Mitternacht als Zeitpunkt`() = scenario {
        advance(Duration.ofHours(13)) // 22:00
        start()
        sync()

        kern().replan()

        // Montag 60 min: Vorwarnung 22:55, Alarm 23:00 (Berlin 22:00 + 60 min = 23:00). Mitternacht liegt dahinter.
        assertEquals(server.clock.now() + Duration.ofMinutes(55), planned())
        advance(Duration.ofMinutes(61))
        kern().trigger()
        // Überziehung seit 23:00, Stopp 00:00 = Mitternacht: der nächste Zeitpunkt nach dem Klingeln ist die Wiederholung.
        assertEquals(server.clock.now() + Duration.ofMinutes(5), planned())
    }

    @Test
    fun `Ein laufender Alarm verstummt um 00 00 beim neuen Tageskontingent und der Alarmkern plant neu`() = scenario {
        startAt2230()
        sync()
        kern().replan()
        advance(Duration.ofMinutes(86)) // 23:56:00; Kontingent war um 23:30 verbraucht
        kern().trigger()
        assertTrue("ring" in effects.calls, "Der Alarm klingelt")
        // Die nächste Wiederholung läge um 00:01, Mitternacht kommt davor.
        assertEquals(midnight, planned())
        effects.calls.clear()

        advance(Duration.ofMinutes(4)) // 00:00
        val due = kern().trigger()

        assertEquals(emptyList<Ausloeser>(), due, "Nichts auszuführen: Mitternacht plant nur neu")
        assertTrue("silence" in effects.calls, "Das neue Kontingent (Dienstag 60 min) lässt Restzeit: der Alarm verstummt")
        assertTrue("ring" !in effects.calls)
        assertEquals(server.clock.now() + Duration.ofMinutes(55), planned(), "Das neue Kontingent bekommt seine eigene Vorwarnung, 5 min vor seinem Ende")
    }

    @Test
    fun `Offline über Mitternacht rechnet das Tablet mit der Vorlage von morgen aus dem Snapshot weiter`() = scenario {
        // Dienstag ab 2026-01-06 gilt eine neue Vorlage mit 30 min; der Snapshot von Montag enthält sie als „morgen“.
        serverSql(
            "INSERT INTO weekly_template_versions (child_id, valid_from, mon, tue, wed, thu, fri, sat, sun) " +
                "VALUES ('${child.childId}', '2026-01-06', 30, 30, 30, 30, 30, 30, 30)",
        )
        advance(Duration.ofHours(13) + Duration.ofMinutes(30)) // 22:30
        start()
        sync() // der Snapshot vom Montag 22:30 trägt Vorlage heute (60) und morgen (30)
        online = false
        advance(Duration.ofMinutes(120)) // Dienstag 00:30, das Tablet ist weiter offline

        val stand = kern().currentStand()!!

        assertEquals(Duration.ofMinutes(30), stand.tageskontingent.gesamt, "Vorlage von morgen")
        assertEquals(Duration.ofMinutes(30), stand.verbrauch, "nur 00:00 bis 00:30 zählt für Dienstag")
        assertEquals(Duration.ZERO, stand.restzeit)
        kern().replan()
        assertEquals(server.clock.now(), planned(), "Restzeit 0 am neuen Tag: der Alarm ist sofort fällig")
    }

    @Test
    fun `Der Alarmkern rechnet die Mitternacht in der Familien-Zeitzone, nicht in der des Geräts`() {
        val vorher = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
            scenario {
                startAt2230()
                sync()
                kern().replan()
                advance(Duration.ofMinutes(86))
                kern().trigger()
                // Berlin-Mitternacht, nicht die von Kiritimati (10:00 UTC).
                assertEquals(midnight, planned())
            }
        } finally {
            TimeZone.setDefault(vorher)
        }
    }
}
