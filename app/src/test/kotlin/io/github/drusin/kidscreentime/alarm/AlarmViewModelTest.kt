package io.github.drusin.kidscreentime.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.domain.bildschirmzeit.KindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Sitzung
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Wochenvorlage
import io.github.drusin.kidscreentime.domain.bildschirmzeit.WochenvorlageVersion
import io.github.drusin.kidscreentime.sync.SyncScenario
import io.github.drusin.kidscreentime.sync.syncScenario
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner

/** Ticket 06: what the buttons of the full-screen Alarm do. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlarmViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val effects = RecordingAlarmEffects()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice(alarmEffects = effects)
        sync()
        block()
    }

    private fun SyncScenario.viewModel(silence: () -> Unit = {}): AlarmViewModel {
        val app = child.app.container
        return AlarmViewModel(app.alarmKern, app.childRepository, NoTestSession, silence)
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(20) }

    @Test
    fun `Der Alarm zeigt die Überziehung der laufenden Sitzung`() = scenario {
        start()
        advance(Duration.ofMinutes(70))

        val vm = viewModel()
        waitFor { vm.state.value.loaded && vm.state.value.stand != null }

        assertEquals(Duration.ofMinutes(10), vm.state.value.stand?.ueberziehung)
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun `Stopp beendet die Sitzung über die Outbox und den Alarm`() = scenario {
        val id = start()
        advance(Duration.ofMinutes(70))
        val silenced = mutableListOf<String>()
        val vm = viewModel { silenced += "silence" }
        waitFor { vm.state.value.stand != null }

        vm.stop()

        waitFor { runCatching { serverSession(id).second }.getOrNull() != null }
        assertEquals(server.clock.now().toEpochMilli(), serverSession(id).second)
        assertTrue(silenced.isNotEmpty(), "Das Klingeln endet")
        waitFor { vm.state.value.finished }
    }

    @Test
    fun `Ton aus beendet nur das Klingeln, die Sitzung läuft weiter und der Alarm bleibt geplant`() = scenario {
        val id = start()
        advance(Duration.ofMinutes(61))
        val silenced = mutableListOf<String>()
        val vm = viewModel { silenced += "silence" }
        waitFor { vm.state.value.stand != null }

        vm.silenceOnce()

        assertEquals(listOf("silence"), silenced)
        assertNull(runCatching { serverSession(id).second }.getOrNull(), "Nichts wurde gestoppt")
        assertNotNull(child.app.container.alarmKern.currentStand()?.laufendeSitzung)
    }

    @Test
    fun `Eine Testsitzung ersetzt den Stand und endet mit Stopp`() = scenario {
        val now = server.clock.now()
        val test = object : TestSession {
            var running = true
            override val active get() = running
            override fun stand(now: Instant) = if (running) KindStand(
                ZoneId.of("UTC"),
                Wochenvorlage(listOf(WochenvorlageVersion.gleichbleibend(LocalDate.of(2000, 1, 1), 2))),
                sitzungen = listOf(Sitzung("test-session", now)),
                vorwarnschwelle = Duration.ofMinutes(1),
            ) else null
            override fun stop() { running = false }
        }
        val app = child.app.container
        val kern = AlarmKern(app.childRepository, AlarmScheduler(context), SharedPreferencesAlarmMemory(context), effects, app.wallClock, test)

        kern.replan()
        val planned = shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()?.triggerAtMs
        assertEquals(now + Duration.ofMinutes(1), planned?.let(Instant::ofEpochMilli), "Die Vorwarnung der Testsitzung")

        AlarmViewModel(kern, app.childRepository, test) {}.stop()
        waitFor { shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm() == null }
        assertFalse(test.active)
    }
}
