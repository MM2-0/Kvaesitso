/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.relayClient.chatDelivery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayInsertConfirmationCollector
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/** Delivery progress of one recipient's gift wrap (NIP-17 DMs). */
@Immutable
data class RecipientDelivery(
    val recipient: HexKey,
    val targetRelays: Set<NormalizedRelayUrl>,
    val acceptedRelays: Set<NormalizedRelayUrl> = emptySet(),
    // The sender's own self-copy wrap: shown in the delivery detail (it matters
    // for multi-device sync) but excluded from "delivered to everyone" and the
    // k/n count, which describe the OTHER participants.
    val isSelf: Boolean = false,
) {
    val isDelivered: Boolean
        get() = acceptedRelays.isNotEmpty()
}

/**
 * How far an outgoing chat message got towards leaving this device.
 *
 * Distinct from relay acceptance: a message is [SENT] the moment it has been
 * encrypted, wrapped and handed to the relay pool, which is long before any
 * relay OK comes back. The bubble is on screen from [SENDING] onwards, so this
 * is what separates "still working on it" from "out of our hands".
 */
enum class ChatSendState {
    /** Shown optimistically; encryption/wrapping/publish has not finished yet. */
    SENDING,

    /** Handed to the relay pool. Relay acceptance is tracked separately. */
    SENT,

    /** The send failed before reaching the relay pool; retryable. */
    FAILED,
}

/**
 * Delivery progress of one outgoing chat message. For DMs [recipients] carries one
 * entry per gift wrap (the sender's self-copy included); for public rooms it is
 * null and [targetRelays]/[acceptedRelays] describe the room's relay set.
 */
@Immutable
data class ChatDelivery(
    val targetRelays: Set<NormalizedRelayUrl>,
    val acceptedRelays: Set<NormalizedRelayUrl> = emptySet(),
    val recipients: List<RecipientDelivery>? = null,
    val sendState: ChatSendState = ChatSendState.SENT,
    // Relays that answered `OK false`, with their reason. A later acceptance clears the entry.
    val rejectedRelays: Map<NormalizedRelayUrl, String> = emptyMap(),
) {
    /**
     * No relay took the message and every targeted relay refused it: it will never arrive, so the
     * UI must say so (with the relay's reason) instead of showing it as still pending.
     */
    val isRejected: Boolean
        get() =
            acceptedRelays.isEmpty() &&
                rejectedRelays.isNotEmpty() &&
                rejectedRelays.keys.containsAll(targetRelays)

    /** The other participants' wraps (self-copy excluded); null for rooms. */
    val otherRecipients: List<RecipientDelivery>?
        get() = recipients?.filterNot { it.isSelf }

    val isFullyAccepted: Boolean
        get() {
            if (sendState != ChatSendState.SENT) return false
            val others = otherRecipients
            return if (others != null) {
                others.isNotEmpty() && others.all { it.isDelivered }
            } else {
                targetRelays.isNotEmpty() && acceptedRelays.containsAll(targetRelays)
            }
        }
}

/**
 * Remembers, per outgoing chat message, which relays were targeted at publish time
 * and which have accepted (relay OK) since — the source for the delivery ticks on
 * own chat bubbles.
 *
 * The relay pool's outbox drops its entry once an event is fully acked, and relay
 * OKs for recipient gift wraps aggregate onto a single aliased Note, losing WHICH
 * recipient's wrap was accepted. This tracker fills both gaps: sends register the
 * `displayed note id -> (recipient, wrap id, target relays)` mapping while it still
 * exists (inside the publish loop), and a persistent OK listener attributes each
 * acceptance back to the message and, for DMs, to the recipient.
 *
 * State is held as one small StateFlow per tracked message, so a relay OK updates
 * and notifies only that message's collectors instead of fanning out through a
 * whole-map flow. In-memory only: history from before an app restart simply has no
 * entry, and the UI falls back to the Note's seen-on relays.
 *
 * Owns a persistent listener on the client: [destroy] MUST be called when the
 * owning Account is discarded (logout / account removal) or the listener leaks.
 */
class ChatDeliveryTracker(
    client: INostrClient,
) {
    private val lock = KmpLock()

    // One flow per tracked (or queried) displayed-note id; LinkedHashMap iteration
    // order is insertion order, used for eviction. Guarded by [lock].
    private val deliveries = LinkedHashMap<HexKey, MutableStateFlow<ChatDelivery?>>()

    // Lock-free fast-path index for the OK listener, which fires for every event
    // the app publishes anywhere: wrap id -> (displayed note id, recipient).
    @Volatile
    private var wrapIndex = mapOf<HexKey, Pair<HexKey, HexKey>>()

    // Lock-free fast-path set of tracked/queried note ids (see [onAccepted]).
    @Volatile
    private var knownIds = setOf<HexKey>()

    // Re-runs the whole send for a message that failed before reaching the relay
    // pool, keyed by displayed note id. Registered by [markSending] and dropped
    // with the message's flow in [flowForLocked]. Guarded by [lock].
    private val retries = mutableMapOf<HexKey, suspend () -> Unit>()

    private val okCollector =
        RelayInsertConfirmationCollector(
            client,
            onRelayRejected = { eventId, relay, reason -> onRejected(eventId, relay.url, reason) },
        ) { eventId, relay ->
            onAccepted(eventId, relay.url)
        }

    /** Registers a public room message published to the room's [targetRelays]. */
    fun trackPublic(
        eventId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        if (targetRelays.isEmpty()) return
        lock.withLock {
            val flow = flowForLocked(eventId)
            flow.value = ChatDelivery(targetRelays, sendState = flow.value.sendStateOrSent())
        }
    }

    /**
     * Registers a message the UI is showing optimistically, before it has been
     * encrypted, wrapped or published. [retry] re-runs the whole send and is
     * what the bubble's failure affordance invokes; it is kept until the
     * message falls out of the tracked window, so a retry stays available for
     * as long as the failed bubble can be tapped.
     *
     * Call [markSent] once the event reaches the relay pool, or [markFailed]
     * if it never got that far.
     */
    fun markSending(
        displayedNoteId: HexKey,
        retry: suspend () -> Unit,
    ) {
        lock.withLock {
            val flow = flowForLocked(displayedNoteId)
            flow.value =
                flow.value?.copy(sendState = ChatSendState.SENDING)
                    ?: ChatDelivery(emptySet(), sendState = ChatSendState.SENDING)
            retries[displayedNoteId] = retry
        }
    }

    /** The send reached the relay pool; relay acceptance takes over from here. */
    fun markSent(displayedNoteId: HexKey) {
        lock.withLock {
            // The action captured whatever the send needed to repeat itself —
            // for a DM, every gift wrap. Once the event is with the relay pool
            // there is nothing to retry, and holding it would pin that graph
            // for as long as the message stays in the window.
            retries.remove(displayedNoteId)
            val flow = deliveries[displayedNoteId] ?: return
            flow.value = flow.value?.copy(sendState = ChatSendState.SENT)
        }
    }

    /**
     * The send failed before reaching the relay pool. The bubble stays on
     * screen carrying a failure marker rather than vanishing — whatever the
     * user typed is never lost — and [retryFor] hands back the action that
     * re-runs it.
     */
    fun markFailed(displayedNoteId: HexKey) {
        lock.withLock {
            val flow = flowForLocked(displayedNoteId)
            flow.value =
                flow.value?.copy(sendState = ChatSendState.FAILED)
                    ?: ChatDelivery(emptySet(), sendState = ChatSendState.FAILED)
        }
    }

    /** The registered re-send for [displayedNoteId], or null if it fell out of the window. */
    fun retryFor(displayedNoteId: HexKey): (suspend () -> Unit)? = lock.withLock { retries[displayedNoteId] }

    /**
     * Registers one recipient's gift wrap of the DM whose chat feed shows
     * [displayedNoteId] (the inner rumor's id).
     */
    fun trackWrap(
        displayedNoteId: HexKey,
        recipient: HexKey,
        wrapId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
        isSelf: Boolean = false,
    ) {
        lock.withLock {
            val flow = flowForLocked(displayedNoteId)
            val current = flow.value

            val existing = current?.recipients.orEmpty()
            val known = existing.firstOrNull { it.recipient == recipient }
            // Replace rather than append. A retry re-runs the same publish loop
            // and would otherwise register every recipient twice, which shows up
            // as a doubled k/n count ("0/2" on a 1:1 DM) and a detail dialog
            // listing everyone twice. Acceptances already collected for this
            // recipient are kept: the wraps are the same events, so relay OKs
            // from the first attempt still count.
            val updated =
                RecipientDelivery(
                    recipient = recipient,
                    targetRelays = (known?.targetRelays ?: emptySet()) + targetRelays,
                    acceptedRelays = known?.acceptedRelays ?: emptySet(),
                    isSelf = isSelf,
                )

            flow.value =
                ChatDelivery(
                    targetRelays = (current?.targetRelays ?: emptySet()) + targetRelays,
                    acceptedRelays = current?.acceptedRelays ?: emptySet(),
                    recipients = existing.filterNot { it.recipient == recipient } + updated,
                    // A DM registers one wrap at a time while the remaining
                    // recipients are still being sealed, so the send is not
                    // done just because the first wrap landed here. Only
                    // [markSent] ends the sending state.
                    sendState = current.sendStateOrSent(),
                )

            wrapIndex = wrapIndex + (wrapId to (displayedNoteId to recipient))
        }
    }

    /**
     * Registers a group message that was broadcast as a single encrypted [wrapId] to
     * the group's [targetRelays] (Concord channels: the whole channel shares one
     * plane wrap, not a per-recipient gift wrap). Relays OK the wrap, but the feed
     * shows the decrypted inner rumor, so [displayedNoteId] (the rumor id) is mapped
     * through [wrapId] — like [trackPublic] (relay ticks, no per-recipient breakdown)
     * but keyed by the wrap the relay actually acknowledges.
     */
    fun trackWrappedPublic(
        displayedNoteId: HexKey,
        wrapId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        if (targetRelays.isEmpty()) return
        lock.withLock {
            val flow = flowForLocked(displayedNoteId)
            flow.value = ChatDelivery(targetRelays, sendState = flow.value.sendStateOrSent())
            // recipient is unused for a relay-only delivery (recipients stays null, so
            // onAccepted never matches on it); reuse the note id as a harmless value.
            wrapIndex = wrapIndex + (wrapId to (displayedNoteId to displayedNoteId))
        }
    }

    fun deliveryFlow(noteId: HexKey): StateFlow<ChatDelivery?> =
        lock.withLock {
            flowForLocked(noteId)
        }

    fun currentFor(noteId: HexKey): ChatDelivery? =
        lock.withLock {
            deliveries[noteId]?.value
        }

    fun destroy() {
        okCollector.destroy()
        lock.withLock {
            deliveries.clear()
            retries.clear()
            wrapIndex = emptyMap()
            knownIds = emptySet()
        }
    }

    internal fun onAccepted(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        // Lock-free negative path: OKs fire for every event the app publishes
        // anywhere; almost all are not chat messages we track.
        if (eventId !in wrapIndex && eventId !in knownIds) return

        lock.withLock {
            val wrapTarget = wrapIndex[eventId]
            if (wrapTarget != null) {
                val (noteId, recipient) = wrapTarget
                val flow = deliveries[noteId] ?: return
                val delivery = flow.value ?: return

                flow.value =
                    delivery.copy(
                        // An OK proves the event left the device, whatever the
                        // send path recorded.
                        sendState = ChatSendState.SENT,
                        acceptedRelays = delivery.acceptedRelays + relay,
                        rejectedRelays = delivery.rejectedRelays - relay,
                        recipients =
                            delivery.recipients?.map {
                                if (it.recipient == recipient) {
                                    it.copy(acceptedRelays = it.acceptedRelays + relay)
                                } else {
                                    it
                                }
                            },
                    )
            } else {
                val flow = deliveries[eventId] ?: return
                val delivery = flow.value ?: return
                flow.value = delivery.copy(sendState = ChatSendState.SENT, acceptedRelays = delivery.acceptedRelays + relay, rejectedRelays = delivery.rejectedRelays - relay)
            }
        }
    }

    internal fun onRejected(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        if (eventId !in wrapIndex && eventId !in knownIds) return

        lock.withLock {
            val noteId = wrapIndex[eventId]?.first ?: eventId
            val flow = deliveries[noteId] ?: return
            val delivery = flow.value ?: return
            // A relay that already accepted the event (e.g. a resend after AUTH) keeps its tick.
            if (relay in delivery.acceptedRelays) return
            flow.value = delivery.copy(sendState = ChatSendState.SENT, rejectedRelays = delivery.rejectedRelays + (relay to reason))
        }
    }

    // Must run under [lock]. Also creates entries for ids queried by the UI
    // before their send registers (compose can win that race), so both paths
    // share the same flow instance.
    private fun flowForLocked(noteId: HexKey): MutableStateFlow<ChatDelivery?> {
        deliveries[noteId]?.let { return it }

        val flow = MutableStateFlow<ChatDelivery?>(null)
        deliveries[noteId] = flow
        knownIds = knownIds + noteId

        while (deliveries.size > MAX_TRACKED) {
            // Most entries here were created by the UI merely rendering a
            // bubble, so plain insertion order would let scrolling a long chat
            // evict a failed message's retry — leaving a tappable warning glyph
            // that does nothing. Anything still holding a retry is spared until
            // nothing else is left to drop.
            val evicted =
                deliveries.keys.firstOrNull { it !in retries }
                    ?: deliveries.keys.first()
            deliveries.remove(evicted)
            retries.remove(evicted)
            knownIds = knownIds - evicted
            wrapIndex = wrapIndex.filterValues { it.first != evicted }
        }

        return flow
    }

    companion object {
        // Delivery state is only rendered on recent messages; a bounded window keeps
        // the maps from growing for the whole session.
        private const val MAX_TRACKED = 500
    }
}

/**
 * The send state to carry into a rebuilt [ChatDelivery]. An entry the UI created
 * by querying ahead of the send (see `flowForLocked`) holds null and has not
 * claimed anything yet, so it reads as [ChatSendState.SENT] — the historical
 * behaviour for every path that never announces a sending phase.
 */
private fun ChatDelivery?.sendStateOrSent(): ChatSendState = this?.sendState ?: ChatSendState.SENT
