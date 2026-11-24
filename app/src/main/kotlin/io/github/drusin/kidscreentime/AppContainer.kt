package io.github.drusin.kidscreentime

import android.content.Context
import android.os.Build
import io.github.drusin.kidscreentime.net.createHttpClient
import io.github.drusin.kidscreentime.pairing.KeystoreTokenStore
import io.github.drusin.kidscreentime.pairing.PairingRepository
import io.github.drusin.kidscreentime.pairing.PairingStorage
import io.github.drusin.kidscreentime.pairing.TokenStore
import io.github.drusin.kidscreentime.parent.ParentRepository
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Manual dependency injection (no Hilt): the one place that wires the app. Created once by [KidScreenTimeApp].
 * Tests build their own with a fake [TokenStore] and a `MockEngine`; ViewModels take what they need from here.
 */
class AppContainer(
    context: Context,
    tokenStore: TokenStore = KeystoreTokenStore(context),
    engine: HttpClientEngine = OkHttp.create(),
) {
    val httpClient = createHttpClient(engine)
    val pairingRepository = PairingRepository(httpClient, tokenStore, PairingStorage(context))
    val parentRepository = ParentRepository(httpClient, pairingRepository)

    /** Suggested name for this device during the Kopplung. */
    val defaultDeviceName: String = Build.MODEL.orEmpty()
}
