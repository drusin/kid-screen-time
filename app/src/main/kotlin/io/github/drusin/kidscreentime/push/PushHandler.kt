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
 * screens reload the Eltern-Snapshot. A Elterngerät also shows the text of the signal as a notification ([ParentNotifier], ticket 14). After the work the [PushSignal] is emitted.
 *
 * Messages that did not decrypt are ignored: the connector hands over undecryptable bytes as "plain text", and
 * only the server can encrypt for this device's keys, so plain text is never ours.
 */
class PushHandler(
    private val pairing: PairingRepository,
    private val child: ChildRepository,
    private val notifier: ParentNotifier = ParentNotifier { _, _ -> },
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
            // Ticket 14: the text is ready-made; the notification needs no network. The reload follows via [signals].
            DeviceRole.PARENT -> signal.text?.let { notifier.show(signal.title.orEmpty(), it) }
            null -> Unit
        }
        _signals.emit(signal)
    }
}
