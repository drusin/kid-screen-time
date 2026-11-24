package io.github.drusin.kidscreentime.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** JSON settings of the REST contract. Unknown fields are ignored (a newer server may add optional fields). */
val ApiJson = Json { ignoreUnknownKeys = true }

/**
 * The one HTTP client of the app (Ktor, JSON, timeouts). Tests pass a `MockEngine`. It never throws for
 * HTTP error codes; callers look at the status (`401`, `403`, `429`).
 */
fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(ApiJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
    }
}
