package io.github.drusin.kidscreentime.api

import kotlinx.serialization.Serializable

/** Answer of the health endpoint. Also proves that the contract module serializes on all platforms. */
@Serializable
data class HealthResponse(val status: String, val serverTimeEpochMillis: Long? = null)
