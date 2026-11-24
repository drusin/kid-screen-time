package io.github.drusin.kidscreentime.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.pairing.FakeTokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Robolectric test setup for ViewModels: an [AppContainer] with a fake token store and a MockEngine whose
 * answers the test sets through [handler]. Reuse it for later screens.
 */
class TestContainer {
    val requests = mutableListOf<HttpRequestData>()
    var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("{}") }
    val container = AppContainer(
        context = ApplicationProvider.getApplicationContext<Context>(),
        tokenStore = FakeTokenStore(),
        engine = MockEngine { request -> requests += request; handler(request) },
    )
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/**
 * Waits (in real time, at most 5 s) until the state satisfies [predicate]. Ktor's MockEngine answers on a
 * background dispatcher, so a ViewModel's request finishes slightly after `submit()` returns.
 */
suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
    withContext(Dispatchers.Default) { withTimeout(5_000) { first(predicate) } }
