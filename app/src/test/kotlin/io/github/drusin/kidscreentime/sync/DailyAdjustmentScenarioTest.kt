package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.alarm.RecordingAlarmEffects
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Ausloeser
import io.github.drusin.kidscreentime.parent.ParentActionResult
import java.time.Duration
import java.time.Instant
import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Ticket 09: a Tagesanpassung booked at the Elterngerät (real app repository, real server) takes effect at the Kindgerät
 * with its next sync: the Alarmkern plans again through `observe` (no direct `replan` call), ringing stops or starts at once.
 */
@RunWith(RobolectricTestRunner::class)
class DailyAdjustmentScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val effects = RecordingAlarmEffects()
    private val alarmManager get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun planned(): Instant? = alarmManager.peekNextScheduledAlarm()?.let { Instant.ofEpochMilli(it.triggerAtMs) }

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    private fun scenario(block: suspend SyncScenario.(CoroutineScope) -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice(alarmEffects = effects)
        sync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        child.app.container.alarmKern.observe(scope)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Ein Abzug während einer Offline-Phase löst nach dem Sync sofort den Alarm aus`() = scenario {
        val parent = pairParentDevice()
        val start = server.clock.now()
        start()
        waitFor { planned() == start + Duration.ofMinutes(55) }
        sync()
        child.offline = true
        advance(Duration.ofMinutes(40))

        val booked = parent.app.container.parentRepository.bookAdjustment(child.childId!!, -30)
        assertTrue(booked is ParentActionResult.Done)
        child.app.container.alarmKern.replan()
        assertEquals(start + Duration.ofMinutes(55), planned(), "Offline weiß das Tablet nichts vom Abzug")

        child.offline = false
        sync()
        waitFor { planned() == server.clock.now() }

        assertEquals(listOf(Ausloeser.ALARM), child.app.container.alarmKern.trigger(), "Restzeit 60 − 30 − 40 liegt unter 0")
        assertTrue("ring" in effects.calls)
    }

    @Test
    fun `Eine Bonuszeit nach dem Sync lässt einen laufenden Alarm verstummen und plant die neue Vorwarnung`() = scenario {
        val parent = pairParentDevice()
        start()
        advance(Duration.ofMinutes(61))
        child.app.container.alarmKern.trigger()
        assertTrue("ring" in effects.calls)
        effects.calls.clear()

        parent.app.container.parentRepository.bookAdjustment(child.childId!!, 30)
        sync()

        waitFor { "silence" in effects.calls }
        waitFor { planned() == server.clock.now() + Duration.ofMinutes(24) }
        assertEquals(listOf("silence"), effects.calls.distinct(), "Nichts klingelt mehr")
    }

    @Test
    fun `Das Zurücknehmen einer Bonuszeit lässt den Alarm nach dem nächsten Sync wieder klingeln`() = scenario {
        val parent = pairParentDevice()
        val repository = parent.app.container.parentRepository
        start()
        repository.bookAdjustment(child.childId!!, 30)
        sync()
        advance(Duration.ofMinutes(70)) // 90 min Kontingent, 20 min Restzeit

        repository.undoLastAdjustment(child.childId!!)
        sync()

        waitFor { planned() == server.clock.now() }
        assertEquals(listOf(Ausloeser.ALARM), child.app.container.alarmKern.trigger())
    }
}
