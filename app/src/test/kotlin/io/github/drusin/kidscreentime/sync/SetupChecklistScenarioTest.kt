package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.MainDispatcherRule
import io.github.drusin.kidscreentime.api.SetupItem
import io.github.drusin.kidscreentime.child.ChildSyncResult
import io.github.drusin.kidscreentime.parent.SnapshotResult
import io.github.drusin.kidscreentime.ui.child.ChildHomeViewModel
import io.github.drusin.kidscreentime.waitFor
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ticket 15: the Einrichtungs-Checkliste from the app's point of view against the real server. The Kindgerät checks with
 * every sync and sends the missing prüfbaren Punkte; the list is the latest state, so a repeated batch or a sync that
 * only succeeds after an offline period carries the check of that moment. The Elterngerät reads it from its snapshot.
 */
@RunWith(RobolectricTestRunner::class)
class SetupChecklistScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath(), block)

    private suspend fun SyncScenario.Device.snapshotMissing(): List<SetupItem> =
        assertIs<SnapshotResult.Loaded>(app.container.parentRepository.loadSnapshot()).snapshot.children.single().missingSetupItems

    @Test
    fun `Der Sync überträgt die fehlenden Punkte, das Elterngerät sieht sie im Snapshot`() = scenario {
        val parent = pairParentDevice()
        val tablet = pairChildDevice()
        tablet.app.setupChecks.missing = listOf(SetupItem.ALARM_VOLUME, SetupItem.NOTIFICATIONS)

        assertEquals(ChildSyncResult.Synced, tablet.sync())

        assertEquals(listOf(SetupItem.NOTIFICATIONS, SetupItem.ALARM_VOLUME), parent.snapshotMissing())
    }

    @Test
    fun `Ist alles erledigt, meldet der nächste Sync vollständig und der Punkt verschwindet bei den Eltern`() = scenario {
        val parent = pairParentDevice()
        val tablet = pairChildDevice()
        tablet.app.setupChecks.missing = listOf(SetupItem.BATTERY)
        tablet.sync()

        tablet.app.setupChecks.missing = emptyList()
        tablet.sync()

        assertEquals(emptyList(), parent.snapshotMissing())
    }

    @Test
    fun `Eine am Tablet im Offline behobene Einstellung zählt beim nächsten erfolgreichen Sync, der Server hat nur den letzten Stand`() = scenario {
        val parent = pairParentDevice()
        val tablet = pairChildDevice()
        tablet.app.setupChecks.missing = listOf(SetupItem.ALARM_VOLUME)
        tablet.sync()

        online = false
        tablet.app.setupChecks.missing = emptyList()
        assertEquals(ChildSyncResult.Unavailable, tablet.sync())
        assertEquals(listOf(SetupItem.ALARM_VOLUME), parent.also { online = true }.snapshotMissing(), "Offline weiß der Server noch den alten Stand")

        advance(Duration.ofMinutes(10))
        tablet.app.setupChecks.missing = listOf(SetupItem.BATTERY)
        tablet.start()
        tablet.sync()

        assertEquals(listOf(SetupItem.BATTERY), parent.snapshotMissing(), "Die Liste beschreibt den Stand des letzten Sync")
    }

    @Test
    fun `Das Banner am Kindgerät steht, solange ein Punkt fehlt, und verschwindet nach dem Beheben beim Zurückkehren in die App`() = scenario {
        val tablet = pairChildDevice()
        tablet.app.setupChecks.missing = listOf(SetupItem.EXACT_ALARMS)
        val viewModel = track(ChildHomeViewModel(tablet.repository, tablet.app.container.wallClock, tablet.app.container.setupChecklist))
        waitFor("Banner") { viewModel.state.value.setupMissing == listOf(SetupItem.EXACT_ALARMS) }

        tablet.app.setupChecks.missing = emptyList()
        viewModel.onResume() // the first call is the start of the screen
        viewModel.onResume()

        waitFor("Banner weg") { viewModel.state.value.setupMissing.isEmpty() }
    }
}
