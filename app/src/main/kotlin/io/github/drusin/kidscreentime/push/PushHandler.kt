package io.github.drusin.kidscreentime.push

import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.child.ChildRepository
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.pairing.PairingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

/**
 * What a push message does at the device (Sync-Protokoll: "nach jedem Push-Signal"). Every decrypted message is a
 * Resync-Signal. A Kindgerät syncs at once ([ChildRepository.sync]); a Elterngerät announces it on [signals] and its
 * screens reload the Eltern-Snapshot. After the work the [PushSignal] is emitted, so ticket 14 can show its text.
 *
 * Messages that did not decrypt are ignored: the connector hands over undecryptable bytes as "plain text", and
 * only the server can encrypt for this device's keys, so plain text is never ours.
 */
class PushHandler(
    private val pairing: PairingRepository,
    private val child: ChildRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _signals = MutableSharedFlow<PushSignal>(extraBufferCapacity = 8)

    /** Emitted after each handled signal, once the Kindgerät synced. Elterngeräte reload when it fires. */
    val signals: SharedFlow<PushSignal> = _signals.asSharedFlow()

    suspend fun onMessage(content: ByteArray, decrypted: Boolean) {
        if (!decrypted) return
        // The signal carries no state: an unreadable body is still a reason to sync.
        val signal = runCatching { json.decodeFromString<PushSignal>(content.decodeToString()) }.getOrDefault(PushSignal())
        when ((pairing.state.value as? DeviceState.Paired)?.device?.role) {
            DeviceRole.CHILD -> child.sync()
            DeviceRole.PARENT, null -> Unit
        }
        _signals.emit(signal)
    }
}
