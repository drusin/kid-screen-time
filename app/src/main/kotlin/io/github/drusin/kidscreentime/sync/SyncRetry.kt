package io.github.drusin.kidscreentime.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.drusin.kidscreentime.KidScreenTimeApp
import io.github.drusin.kidscreentime.child.ChildSyncResult
import java.time.Duration

/**
 * The repetition of a failed sync (Sync-Protokoll: "scheitert er, wiederholt ihn ein WorkManager-Job mit
 * Netzwerk-Bedingung"). It is the only repetition: there is no periodic job and no separate online path.
 * [ChildRepository.sync][io.github.drusin.kidscreentime.child.ChildRepository.sync] calls [schedule] whenever the
 * server could not be reached.
 */
fun interface SyncRetry {
    fun schedule()
}

/** Schedules [SyncRetryWorker] as unique work that waits for a network connection. */
class WorkManagerSyncRetry(private val context: Context) : SyncRetry {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncRetryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        // KEEP: one waiting job is enough, further failures do not pile up more of them.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "sync-retry"
    }
}

/** Runs the one sync path of the Kindgerät. Fails with a retry (WorkManager's backoff) while the server is unreachable. */
class SyncRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as KidScreenTimeApp).container.childRepository
        return when (repository.sync()) {
            ChildSyncResult.Synced, ChildSyncResult.Unauthorized -> Result.success()
            ChildSyncResult.Unavailable -> Result.retry()
        }
    }
}
