package io.github.drusin.kidscreentime.sync

import io.github.drusin.kidscreentime.waitFor
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.drusin.kidscreentime.KidScreenTimeApp
import io.github.drusin.kidscreentime.child.ChildSyncResult
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Ticket 08: a failed sync is repeated by one WorkManager job that waits for a network; unlocking the device syncs, too.
 * Real WorkManager (test initialisation), real worker, real sync against the real server.
 */
@RunWith(RobolectricTestRunner::class)
class SyncRetryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    /** The test WorkManager keeps its own in-memory Room database; nothing else closes it (a leaked `SQLiteDatabase` otherwise). */
    @After
    fun closeWorkManager() = WorkManagerTestInitHelper.closeWorkDatabase()

    private fun workInfos() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerSyncRetry.WORK_NAME).get()

    private fun useContainerOf(device: SyncScenario.Device) =
        (context as KidScreenTimeApp).injectContainerForTest(device.app.container)

    @Test
    fun `Scheitert der Sync, wartet ein Job mit Netzwerk-Bedingung und liefert die Outbox, sobald Netz da ist`() =
        syncScenario(folder.newFolder().toPath()) {
            val device = pairChildDevice(syncRetry = WorkManagerSyncRetry(context))
            useContainerOf(device)
            device.sync()
            val t0 = server.clock.now().toEpochMilli()
            online = false
            val id = device.start()
            assertEquals(ChildSyncResult.Unavailable, device.sync())
            assertEquals(ChildSyncResult.Unavailable, device.sync())

            val waiting = workInfos().single()
            assertEquals(WorkInfo.State.ENQUEUED, waiting.state, "Ein Job, auch nach zwei Fehlschlägen")
            assertEquals(NetworkType.CONNECTED, waiting.constraints.requiredNetworkType)

            // The network is back: the constraint is met and the worker runs the one sync path.
            online = true
            advance(Duration.ofMinutes(3))
            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(waiting.id)
            waitFor { device.app.database.outboxDao().all().isEmpty() }

            assertEquals(Triple(t0, null, null), serverSession(id))
            assertTrue(serverRecordedOffline(id))
            waitFor { workInfos().single().state == WorkInfo.State.SUCCEEDED }
        }

    @Test
    fun `Scheitert der Job selbst, versucht WorkManager es mit Backoff noch einmal`() =
        syncScenario(folder.newFolder().toPath()) {
            val device = pairChildDevice(syncRetry = WorkManagerSyncRetry(context))
            useContainerOf(device)
            device.sync()
            online = false
            device.start()
            device.sync()

            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(workInfos().single().id)
            waitFor { workInfos().single().runAttemptCount >= 1 }
            waitFor { workInfos().single().state == WorkInfo.State.ENQUEUED }

            assertEquals(1, device.app.database.outboxDao().all().size, "Nichts ging verloren")
        }

    @Test
    fun `Beim Entsperren des Geräts synct das Kindgerät`() = syncScenario(folder.newFolder().toPath()) {
        val device = pairChildDevice()
        useContainerOf(device)
        device.sync()
        val id = device.start()
        assertTrue(device.outbox().isNotEmpty())

        // The app registered the receiver for the unlock broadcast; the delivery is the system's job, so call it directly.
        val registered = shadowOf(context as android.app.Application).registeredReceivers
        assertTrue(registered.any { it.broadcastReceiver is UnlockSyncReceiver && it.intentFilter.hasAction(Intent.ACTION_USER_PRESENT) })
        UnlockSyncReceiver().onReceive(context, Intent(Intent.ACTION_USER_PRESENT))
        waitFor { device.app.database.outboxDao().all().isEmpty() }

        assertEquals(id, serverSnapshot().status.runningSession?.id)
    }
}
