package io.github.drusin.kidscreentime.api

import kotlinx.serialization.Serializable

/**
 * What the server pushes to a device (the plaintext inside the WebPush encryption). Every signal is a Resync-Signal:
 * the device syncs. The signal carries no state, so a lost or duplicated signal is harmless.
 *
 * [title] and [text] are optional and meant for the Eltern-Benachrichtigungen (ticket 14): the device shows them
 * as a notification straight from the signal, without network access of its own (Doze). Unknown fields are
 * ignored on both sides, so later tickets can add the state a notification needs.
 */
@Serializable
data class PushSignal(
    val title: String? = null,
    val text: String? = null,
)
