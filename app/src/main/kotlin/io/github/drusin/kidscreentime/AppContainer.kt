package io.github.drusin.kidscreentime

import android.content.Context
import android.os.Build
import io.github.drusin.kidscreentime.alarm.AlarmEffects
import io.github.drusin.kidscreentime.alarm.AlarmKern
import io.github.drusin.kidscreentime.alarm.AlarmScheduler
import io.github.drusin.kidscreentime.alarm.AndroidAlarmEffects
import io.github.drusin.kidscreentime.alarm.SharedPreferencesAlarmMemory
import io.github.drusin.kidscreentime.alarm.TestSession
import io.github.drusin.kidscreentime.alarm.createTestSession
import io.github.drusin.kidscreentime.child.ChildRepository
import io.github.drusin.kidscreentime.clock.AndroidMonotonicClock
import io.github.drusin.kidscreentime.db.AppDatabase
import io.github.drusin.kidscreentime.domain.clock.MonotonicClock
import io.github.drusin.kidscreentime.domain.clock.SystemWallClock
import io.github.drusin.kidscreentime.domain.clock.WallClock
import io.github.drusin.kidscreentime.net.createHttpClient
import io.github.drusin.kidscreentime.pairing.KeystoreTokenStore
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.github.drusin.kidscreentime.pairing.PairingStorage
import io.github.drusin.kidscreentime.pairing.TokenStore
import io.github.drusin.kidscreentime.parent.ParentRepository
import io.github.drusin.kidscreentime.push.AndroidParentNotifier
import io.github.drusin.kidscreentime.push.ParentNotifier
import io.github.drusin.kidscreentime.push.PushHandler
import io.github.drusin.kidscreentime.push.PushRepository
import io.github.drusin.kidscreentime.push.PushStorage
import io.github.drusin.kidscreentime.qr.GmsQrScanner
import io.github.drusin.kidscreentime.qr.QrScanner
import io.github.drusin.kidscreentime.setup.AndroidSetupChecks
import io.github.drusin.kidscreentime.setup.SetupChecklist
import io.github.drusin.kidscreentime.setup.SetupChecks
import io.github.drusin.kidscreentime.sync.SyncRetry
import io.github.drusin.kidscreentime.sync.WorkManagerSyncRetry
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Manual dependency injection (no Hilt): the one place that wires the app. Created once by [KidScreenTimeApp], which also starts [AlarmKern.observe].
 * Tests build their own with a fake [TokenStore], a `MockEngine`, a `ManualClock` and an in-memory [AppDatabase];
 * ViewModels take what they need from here.
 */
class AppContainer(
    context: Context,
    tokenStore: TokenStore = KeystoreTokenStore(context),
    engine: HttpClientEngine = OkHttp.create(),
    val wallClock: WallClock = SystemWallClock,
    val monotonicClock: MonotonicClock = AndroidMonotonicClock,
    /** The one Room database (Kind-Snapshot and Outbox). */
    val database: AppDatabase = AppDatabase.create(context),
    /** The scanner needs an Activity context, so screens build it with their own. */
    val qrScannerFactory: (Context) -> QrScanner = { GmsQrScanner(it) },
    /** What the Alarmkern makes happen at the device (sound, full screen). Tests record instead. */
    val alarmEffects: AlarmEffects = AndroidAlarmEffects(context),
    /** The debug build's local test Sitzung; release has none. */
    val testSession: TestSession = createTestSession(context, wallClock),
    /** Repeats a failed sync (WorkManager). Tests record instead. */
    val syncRetry: SyncRetry = WorkManagerSyncRetry(context),
    /** Tells the push distributor that this app no longer wants messages (after the Kopplung ended). Tests do nothing. */
    unregisterPush: () -> Unit = { UnifiedPush.unregister(context) },
    /** Shows the Eltern-Benachrichtigungen (ticket 14). Tests record instead. */
    parentNotifier: ParentNotifier = AndroidParentNotifier(context),
    /** The system checks of the Einrichtungs-Checkliste (ticket 15). Tests steer them. */
    setupChecks: SetupChecks = AndroidSetupChecks(context),
) {
    val httpClient = createHttpClient(engine)
    val pairingRepository: PairingRepository = PairingRepository(httpClient, tokenStore, PairingStorage(context), onUnpaired = {
        // An entkoppeltes Gerät gets no push signals any more (the server stopped them); the distributor and our records follow.
        unregisterPush()
        pushRepository.onUnregistered()
    })
    val parentRepository = ParentRepository(httpClient, pairingRepository, database.parentSnapshotDao(), wallClock)
    val setupChecklist = SetupChecklist(setupChecks, context)
    val childRepository = ChildRepository(httpClient, pairingRepository, database, wallClock, monotonicClock, syncRetry, setupChecklist)
    val pushRepository: PushRepository = PushRepository(httpClient, pairingRepository, PushStorage(context))
    val pushHandler = PushHandler(pairingRepository, childRepository, parentNotifier)

    /** For work that outlives a screen, e.g. what a push message triggers. Lives as long as the process. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val alarmKern = AlarmKern(
        childRepository,
        AlarmScheduler(context),
        SharedPreferencesAlarmMemory(context),
        alarmEffects,
        wallClock,
        testSession,
    )

    /** Suggested name for this device during the Kopplung. */
    val defaultDeviceName: String = Build.MODEL.orEmpty()
}
