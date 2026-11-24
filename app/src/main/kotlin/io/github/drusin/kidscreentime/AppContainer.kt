package io.github.drusin.kidscreentime

import android.content.Context
import android.os.Build
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
import io.github.drusin.kidscreentime.qr.GmsQrScanner
import io.github.drusin.kidscreentime.qr.QrScanner
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Manual dependency injection (no Hilt): the one place that wires the app. Created once by [KidScreenTimeApp].
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
) {
    val httpClient = createHttpClient(engine)
    val pairingRepository = PairingRepository(httpClient, tokenStore, PairingStorage(context))
    val parentRepository = ParentRepository(httpClient, pairingRepository)
    val childRepository = ChildRepository(httpClient, pairingRepository, database, wallClock, monotonicClock)

    /** Suggested name for this device during the Kopplung. */
    val defaultDeviceName: String = Build.MODEL.orEmpty()
}
