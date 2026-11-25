package io.github.drusin.kidscreentime.sync

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.alarm.RecordingAlarmEffects
import io.github.drusin.kidscreentime.parent.ParentActionResult
import io.github.drusin.kidscreentime.waitFor
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Ticket 10: Regeln und Wochenvorlage vom Elterngerät erreichen das Kindgerät mit dem nächsten Sync. */
@RunWith(RobolectricTestRunner::class)
class RulesScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun planned(): Instant? = alarmManager.peekNextScheduledAlarm()?.let { Instant.ofEpochMilli(it.triggerAtMs) }

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice(alarmEffects = RecordingAlarmEffects())
        sync()
        child.observeAlarm()
        block()
    }

    @Test
    fun `Eine neue Vorwarnschwelle plant den Alarmkern nach dem Sync mit dem neuen Wert`() = scenario {
        val parent = pairParentDevice()
        val start = server.clock.now()
        start()
        waitFor("Vorwarnung nach 55 min") { planned() == start + Duration.ofMinutes(55) }

        val result = parent.app.container.parentRepository.setRules(child.childId!!, "Mia", 15, 60)
        assertTrue(result is ParentActionResult.Done)
        sync()

        waitFor("Vorwarnung nach 45 min") { planned() == start + Duration.ofMinutes(45) }
        assertEquals(15, child.storedSnapshot()!!.warningThresholdMinutes)
    }

    @Test
    fun `Name und Wochenvorlage kommen am Kindgerät an, die Vorlage gilt erst ab morgen`() = scenario {
        val parent = pairParentDevice()
        val repository = parent.app.container.parentRepository

        repository.setRules(child.childId!!, "Mimi", 5, 60)
        repository.setWeeklyTemplate(child.childId!!, listOf(30, 30, 30, 30, 30, 30, 30))
        val snapshot = sync().let { child.storedSnapshot()!! }

        assertEquals("Mimi", snapshot.childName)
        assertEquals(60, snapshot.status.allowance.totalMinutes, "Heute bleibt das Kontingent")
        assertEquals(30, snapshot.templateTomorrow.minutes[1])
    }
}
