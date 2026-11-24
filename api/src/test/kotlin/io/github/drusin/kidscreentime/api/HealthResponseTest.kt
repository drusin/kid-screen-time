package io.github.drusin.kidscreentime.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthResponseTest {
    @Test
    fun `Health-Antwort wird als JSON mit dem Feld status serialisiert`() {
        assertEquals("""{"status":"ok"}""", Json.encodeToString(HealthResponse.serializer(), HealthResponse("ok")))
    }

    @Test
    fun `Health-Antwort wird aus JSON gelesen`() {
        assertEquals(HealthResponse("ok"), Json.decodeFromString(HealthResponse.serializer(), """{"status":"ok"}"""))
    }
}
