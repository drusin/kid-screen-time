package io.github.drusin.kidscreentime.sync

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.MainDispatcherRule
import io.github.drusin.kidscreentime.alarm.RecordingAlarmEffects
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.parent.ParentActionResult
import io.github.drusin.kidscreentime.server.TestServer
import io.github.drusin.kidscreentime.ui.child.ChildAction
import io.github.drusin.kidscreentime.ui.child.ChildHomeViewModel
import io.github.drusin.kidscreentime.waitFor
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Ticket 12: Entkoppeln und Ersetzen aus Sicht der App gegen den echten Server. Ein ersetztes oder entkoppeltes Kindgerät
 * bekommt `401`, verwirft seine Outbox und zeigt "Gerät wurde entkoppelt"; das neue Kindgerät übernimmt eine laufende
 * Sitzung über die Sitzungs-UUID im Snapshot.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceManagementScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath(), block)

    /** Pairs a new Kindgerät for the Kind of [child] the way the Elterngerät does: Kopplungscode für das Kind, am neuen Gerät eingelöst. */
    private suspend fun SyncScenario.replaceChildDevice(): SyncScenario.Device {
        val childId = child.childId
        val device = device()
        val code = server.services.pairing.createCode(DeviceRole.CHILD, childId = childId)
        assertEquals(io.github.drusin.kidscreentime.pairing.PairResult.Success, device.app.container.pairingRepository.pair(TestServer.PUBLIC, code.code, "Neues Tablet"))
        device.childId = childId
        return device
    }

    @Test
    fun `Das ersetzte Kindgerät bekommt 401, verwirft seine Outbox und zeigt Gerät wurde entkoppelt`() = scenario {
        val old = pairChildDevice()
        val sessionId = old.start()
        old.sync()
        advance(Duration.ofMinutes(10))
        old.stop(sessionId)
        val unsent = old.start()
        assertEquals(2, old.outbox().size)
        val newDevice = replaceChildDevice()

        assertEquals(ChildSyncResult.Unauthorized, old.sync())

        assertTrue(old.outbox().isEmpty(), "Die Outbox ist verworfen")
        assertNull(old.storedSnapshot(), "Der Stand gehört dem neuen Gerät")
        assertEquals(DeviceState.Revoked, old.app.container.pairingRepository.state.value)
        assertNull(old.app.container.pairingRepository.token())
        assertEquals(1, old.app.pushUnregistrations, "Der Push-Verteiler wird abgemeldet")
        // Weder der Stopp noch der neue Start der alten Outbox sind beim Server angekommen.
        assertEquals(null, serverSession(sessionId).second, "Der Stopp der alten Outbox wurde nicht übernommen")
        assertTrue(serverSnapshot().todaySessions.none { it.id == unsent }, "Der neue Start der alten Outbox kam nicht an")
        assertIs<DeviceState.Paired>(newDevice.app.container.pairingRepository.state.value)
    }

    @Test
    fun `Das neue Kindgerät übernimmt die laufende Sitzung, erkennt sie als eigene und kann sie stoppen`() = scenario {
        val old = pairChildDevice()
        val sessionId = old.start()
        old.sync()
        advance(Duration.ofMinutes(10))
        val newDevice = replaceChildDevice()

        assertEquals(ChildSyncResult.Synced, newDevice.sync())

        assertEquals(sessionId, newDevice.storedSnapshot()?.status?.runningSession?.id)
        val viewModel = track(ChildHomeViewModel(newDevice.app.container.childRepository, newDevice.app.container.wallClock, newDevice.app.container.setupChecklist))
        waitFor("Sitzung am neuen Gerät laufend") { viewModel.state.value.stand?.laufendeSitzung?.id == sessionId }
        assertEquals(ChildAction.STOP, viewModel.state.value.action)
        assertEquals(Zustand.LAEUFT, viewModel.state.value.stand?.zustand)

        viewModel.onAction()

        waitFor("Sitzung am Server beendet") { serverSession(sessionId).second != null }
        assertEquals(Duration.ofMinutes(10).toMillis(), serverSnapshot().status.usedMillis)
        assertNull(serverSnapshot().status.runningSession)
    }

    @Test
    fun `Beim Entkoppeln storniert das alte Kindgerät seinen Alarm und die Alarm-Erinnerung`() = scenario {
        val effects = RecordingAlarmEffects()
        val old = pairChildDevice(alarmEffects = effects)
        old.sync()
        old.observeAlarm()
        old.start()
        old.sync()
        waitFor("Vorwarnung geplant") { alarmManager.peekNextScheduledAlarm() != null }
        val newDevice = replaceChildDevice()
        newDevice.sync()

        old.sync()

        waitFor("Alarm storniert") { alarmManager.peekNextScheduledAlarm() == null }
        assertNotNull(newDevice.storedSnapshot()?.status?.runningSession, "Das neue Gerät führt die Sitzung weiter")
    }

    @Test
    fun `Ein Elterngerät entkoppelt das Kindgerät, das nächste Sync des Tablets bekommt 401`() = scenario {
        val parent = pairParentDevice()
        val tablet = pairChildDevice()
        tablet.sync()
        val deviceId = (tablet.app.container.pairingRepository.state.value as DeviceState.Paired).device.deviceId

        val result = parent.app.container.parentRepository.unpairDevice(deviceId)

        val snapshot = assertIs<ParentActionResult.Done>(result).snapshot
        assertTrue(snapshot.devices.none { it.role == DeviceRole.CHILD })
        assertEquals(ChildSyncResult.Unauthorized, tablet.sync())
        assertEquals(DeviceState.Revoked, tablet.app.container.pairingRepository.state.value)
    }

    @Test
    fun `Das letzte Elterngerät lässt sich nicht entkoppeln, mit einem zweiten geht auch das eigene`() = scenario {
        val mama = pairParentDevice()
        val repository = mama.app.container.parentRepository
        val mamaId = checkNotNull(repository.ownDeviceId)

        assertEquals(ParentActionResult.Rejected, repository.unpairDevice(mamaId))
        assertIs<DeviceState.Paired>(mama.app.container.pairingRepository.state.value)

        val papa = pairParentDevice()
        assertIs<ParentActionResult.Done>(repository.unpairDevice(mamaId))

        assertEquals(DeviceState.Revoked, mama.app.container.pairingRepository.state.value, "Das eigene Gerät zeigt Gerät wurde entkoppelt")
        assertIs<DeviceState.Paired>(papa.app.container.pairingRepository.state.value)
    }

    @Test
    fun `Ein entkoppeltes Elterngerät bekommt 401 und zeigt Gerät wurde entkoppelt`() = scenario {
        val mama = pairParentDevice()
        val papa = pairParentDevice()
        papa.app.container.parentRepository.unpairDevice(checkNotNull(mama.app.container.parentRepository.ownDeviceId))

        val result = mama.app.container.parentRepository.loadSnapshot()

        assertEquals(io.github.drusin.kidscreentime.parent.SnapshotResult.Unauthorized, result)
        assertEquals(DeviceState.Revoked, mama.app.container.pairingRepository.state.value)
    }

    @Test
    fun `Ein Elterngerät ohne Netz liest den zuletzt geladenen Stand vom echten Server und bucht nichts`() = scenario {
        val mama = pairParentDevice()
        pairChildDevice()
        assertIs<io.github.drusin.kidscreentime.parent.SnapshotResult.Loaded>(mama.app.container.parentRepository.loadSnapshot())
        mama.offline = true

        val result = mama.app.container.parentRepository.loadSnapshot()

        val cached = assertIs<io.github.drusin.kidscreentime.parent.SnapshotResult.Cached>(result)
        assertEquals("Mia", cached.snapshot.children.single().name)
        assertEquals(ParentActionResult.Unavailable, mama.app.container.parentRepository.bookAdjustment(cached.snapshot.children.single().childId, 5))
    }
}
