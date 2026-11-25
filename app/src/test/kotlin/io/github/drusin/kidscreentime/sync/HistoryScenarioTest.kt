package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.MainDispatcherRule
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.parent.HistoryResult
import io.github.drusin.kidscreentime.parent.SnapshotResult
import io.github.drusin.kidscreentime.ui.await
import io.github.drusin.kidscreentime.ui.parent.HistoryViewModel
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ticket 16: der Verlauf am Elterngerät (echtes `ParentRepository` gegen den echten Server). Die Uhr beginnt Montag
 * 2026-01-05 09:00 Berlin.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryScenarioTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    private fun scenario(block: suspend SyncScenario.() -> Unit) = syncScenario(folder.newFolder().toPath()) {
        pairChildDevice()
        sync()
        block()
    }

    @Test
    fun `Die Letzten Tage der Startseite und der volle Verlauf zeigen eine Sitzung über Mitternacht an beiden Tagen`() = scenario {
        val parent = pairParentDevice()
        advance(Duration.ofHours(14) + Duration.ofMinutes(30)) // Montag 23:30
        val id = start()
        sync()
        advance(Duration.ofMinutes(50)) // Dienstag 00:20
        stop(id)
        sync()
        advance(Duration.ofDays(1)) // Mittwoch

        val snapshot = (parent.app.container.parentRepository.loadSnapshot() as SnapshotResult.Loaded).snapshot
        val recent = snapshot.children.single().recentDays
        val history = (parent.app.container.parentRepository.history(child.childId!!) as HistoryResult.Loaded).history

        assertEquals(listOf(Duration.ZERO.toMillis(), Duration.ofMinutes(20).toMillis(), Duration.ofMinutes(30).toMillis()), history.days.map { it.usedMillis })
        assertEquals(listOf("2026-01-06", "2026-01-05"), recent.map { it.day })
        assertEquals(listOf(Duration.ofMinutes(20).toMillis(), Duration.ofMinutes(30).toMillis()), recent.map { it.usedMillis })
        val tuesday = history.days[1].sessions.single()
        assertTrue(tuesday.fromPreviousDay)
        assertEquals(SessionEndReason.STOPPED, tuesday.endReason)
        assertTrue(history.days[2].sessions.single().continuesNextDay)
    }

    @Test
    fun `Ohne Netz zeigt der Verlauf einen Hinweis statt Daten und lädt nach Rückkehr des Netzes`() = scenario {
        val parent = pairParentDevice()
        advance(Duration.ofDays(1))
        parent.offline = true

        val viewModel = track(HistoryViewModel(parent.app.container.parentRepository, child.childId!!))
        val offlineState = viewModel.state.await { !it.loading }

        assertTrue(offlineState.unavailable)
        assertTrue(offlineState.days.isEmpty())

        parent.offline = false
        viewModel.load()
        val online = viewModel.state.await { !it.loading && it.days.isNotEmpty() }

        assertFalse(online.unavailable)
        assertEquals(listOf("2026-01-06", "2026-01-05"), online.days.map { it.day })
    }
}
