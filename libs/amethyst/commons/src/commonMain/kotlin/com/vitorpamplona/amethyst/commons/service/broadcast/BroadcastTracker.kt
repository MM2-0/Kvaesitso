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
package com.vitorpamplona.amethyst.commons.service.broadcast

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tracks event broadcasts to relays with live progress updates.
 *
 * Provides:
 * - Real-time progress as relays respond
 * - Detailed per-relay success/error information
 * - Retry functionality for failed relays
 */
class BroadcastTracker(
    /**
     * The author's NIP-65 outbox relays the event must reach to be out ([BroadcastEvent.isOut]),
     * read when a broadcast starts. Callers leave out relays known to be unreachable.
     */
    private val outboxRelays: () -> Set<NormalizedRelayUrl>,
) {
    companion object {
        private const val TAG = "BroadcastTracker"
        const val TIMEOUT_SECONDS = 10L
    }

    private val _activeBroadcasts = MutableStateFlow<ImmutableList<BroadcastEvent>>(persistentListOf())
    val activeBroadcasts: StateFlow<ImmutableList<BroadcastEvent>> = _activeBroadcasts.asStateFlow()

    /**
     * Tracks an event broadcast to relays with live progress updates.
     *
     * @param event The Nostr event to broadcast
     * @param relays Target relays to send to
     * @param client The Nostr client for sending
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun trackBroadcast(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
    ) {
        // nothing would be sent, and an entry with no relays is neither out nor failed.
        if (relays.isEmpty()) return

        val trackingId = RandomInstance.randomChars(16)

        val broadcast =
            BroadcastEvent(
                id = trackingId,
                event = event,
                targetRelays = relays.toList(),
                outboxRelays = outboxRelays() intersect relays,
            )

        // Add to active broadcasts and cache event for retries
        _activeBroadcasts.update { (it + broadcast).toImmutableList() }

        Log.d(TAG) { "Starting broadcast $trackingId (kind ${event.kind}) to ${relays.size} relays" }

        val resultChannel = Channel<RelayResponse>(UNLIMITED)

        val subscription =
            object : RelayConnectionListener {
                override fun onCannotConnect(
                    relay: IRelayClient,
                    errorMessage: String,
                ) {
                    if (relay.url in relays) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error(errorMessage),
                                connectionError = true,
                            ),
                        )
                        Log.d(TAG) { "[$trackingId] Cannot connect to ${relay.url}: $errorMessage" }
                    }
                }

                override fun onDisconnected(relay: IRelayClient) {
                    if (relay.url in relays) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error("Relay disconnected before completion"),
                                connectionError = true,
                            ),
                        )
                        Log.d(TAG) { "[$trackingId] Disconnected from ${relay.url}" }
                    }
                }

                override suspend fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    super.onIncomingMessage(relay, msgStr, msg)

                    when (msg) {
                        is OkMessage -> {
                            if (msg.eventId == event.id) {
                                resultChannel.trySend(RelayResponse.of(relay.url, msg))
                                Log.d(TAG) { "[$trackingId] Response from ${relay.url}: success=${msg.success} message=${msg.message}" }
                            }
                        }
                    }
                }
            }

        try {
            client.addConnectionListener(subscription)

            val finalBroadcast =
                coroutineScope {
                    val resultCollector =
                        async {
                            val receivedRelays = mutableSetOf<NormalizedRelayUrl>()
                            val connectionErrors = mutableMapOf<NormalizedRelayUrl, RelayResult>()
                            var currentBroadcast = broadcast
                            val started = TimeSource.Monotonic.markNow()

                            withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
                                while (receivedRelays.size < relays.size) {
                                    val response = resultChannel.receive()

                                    // Skip if already received (don't override success)
                                    if (response.relay in receivedRelays) continue

                                    // The client resends once it reconnects, so a dropped or failed
                                    // connection is not the relay's answer: keep waiting for its OK.
                                    if (response.connectionError) {
                                        connectionErrors[response.relay] = response.result
                                        continue
                                    }

                                    // Rate-limited with a back-off the client waits out and then resends:
                                    // shown as retrying, its refusal kept in case no later OK lands.
                                    if (response.backsOffWithin(started)) {
                                        connectionErrors[response.relay] = response.result
                                        currentBroadcast = currentBroadcast.withResult(response.relay, RelayResult.Retrying)
                                        _activeBroadcasts.update { list -> list.replacing(currentBroadcast) }
                                        continue
                                    }

                                    receivedRelays.add(response.relay)
                                    currentBroadcast = currentBroadcast.withResult(response.relay, response.result)

                                    // Update active broadcasts with new progress
                                    _activeBroadcasts.update { list ->
                                        list.replacing(currentBroadcast)
                                    }
                                }
                            }

                            // Relays that never answered: the connection error or held refusal if there was one, else a timeout
                            relays.filter { it !in receivedRelays }.forEach { relay ->
                                currentBroadcast = currentBroadcast.withResult(relay, connectionErrors[relay] ?: RelayResult.Timeout)
                            }

                            currentBroadcast
                        }

                    // Send after setting up listener
                    client.publish(event, relays)

                    resultCollector.await()
                }

            resultChannel.close()

            // Remove from active, emit to completed
            _activeBroadcasts.update { list ->
                list.replacing(finalBroadcast)
            }

            Log.d(TAG) { "Broadcast $trackingId complete: ${finalBroadcast.successCount}/${finalBroadcast.totalRelays} success" }
        } finally {
            client.removeConnectionListener(subscription)
        }
    }

    /**
     * Marks relays as Retrying in an existing broadcast.
     * Call this before starting the retry to show immediate feedback.
     */
    fun markRelaysRetrying(
        broadcastId: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        _activeBroadcasts.update { list ->
            list
                .map { broadcast ->
                    if (broadcast.id == broadcastId) {
                        var updated = broadcast
                        relays.forEach { relay ->
                            updated = updated.withResult(relay, RelayResult.Retrying)
                        }
                        updated
                    } else {
                        broadcast
                    }
                }.toImmutableList()
        }
    }

    /**
     * Retries sending an event to failed relays using cached event.
     * Updates the existing broadcast in-place with retry results.
     *
     * @param broadcast The broadcast to retry (must be in activeBroadcasts or recently completed)
     * @param client The Nostr client
     * @param specificRelay Optional specific relay to retry (null = all failed)
     * @return Updated BroadcastEvent or null if event not in cache
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun retry(
        broadcast: BroadcastEvent,
        client: INostrClient,
        specificRelay: NormalizedRelayUrl? = null,
    ): BroadcastEvent {
        val event = broadcast.event

        val relaysToRetry =
            if (specificRelay != null) {
                setOf(specificRelay)
            } else {
                broadcast.failedRelays.toSet()
            }

        if (relaysToRetry.isEmpty()) {
            return broadcast
        }

        // Mark relays as retrying for immediate feedback
        markRelaysRetrying(broadcast.id, relaysToRetry)

        // If broadcast not in active list, re-add it
        if (_activeBroadcasts.value.none { it.id == broadcast.id }) {
            _activeBroadcasts.update { list ->
                var updated = broadcast
                relaysToRetry.forEach { relay ->
                    updated = updated.withResult(relay, RelayResult.Retrying)
                }
                (list + updated).toImmutableList()
            }
        }

        // Setup result collection
        val resultChannel = Channel<RelayResponse>(UNLIMITED)

        val subscription =
            object : RelayConnectionListener {
                override fun onCannotConnect(
                    relay: IRelayClient,
                    errorMessage: String,
                ) {
                    if (relay.url in relaysToRetry) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error(errorMessage),
                                connectionError = true,
                            ),
                        )
                        Log.d(TAG) { "[${broadcast.id}] Retry cannot connect to ${relay.url}: $errorMessage" }
                    }
                }

                override fun onDisconnected(relay: IRelayClient) {
                    if (relay.url in relaysToRetry) {
                        resultChannel.trySend(
                            RelayResponse(
                                relay = relay.url,
                                result = RelayResult.Error("Relay disconnected before completion"),
                                connectionError = true,
                            ),
                        )
                        Log.d(TAG) { "[${broadcast.id}] Retry disconnected from ${relay.url}" }
                    }
                }

                override suspend fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    super.onIncomingMessage(relay, msgStr, msg)

                    when (msg) {
                        is OkMessage -> {
                            if (msg.eventId == event.id) {
                                resultChannel.trySend(RelayResponse.of(relay.url, msg))
                                Log.d(TAG) { "[${broadcast.id}] Retry response from ${relay.url}: success=${msg.success}" }
                            }
                        }
                    }
                }
            }

        client.addConnectionListener(subscription)

        val finalBroadcast =
            coroutineScope {
                val resultCollector =
                    async {
                        val receivedRelays = mutableSetOf<NormalizedRelayUrl>()
                        val connectionErrors = mutableMapOf<NormalizedRelayUrl, RelayResult>()
                        var currentBroadcast = _activeBroadcasts.value.find { it.id == broadcast.id } ?: broadcast
                        val started = TimeSource.Monotonic.markNow()

                        withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
                            while (receivedRelays.size < relaysToRetry.size) {
                                val response = resultChannel.receive()

                                if (response.relay !in relaysToRetry) continue
                                if (response.relay in receivedRelays) continue
                                if (response.connectionError) {
                                    connectionErrors[response.relay] = response.result
                                    continue
                                }

                                // Already shown as retrying; keep the refusal in case no later OK lands.
                                if (response.backsOffWithin(started)) {
                                    connectionErrors[response.relay] = response.result
                                    continue
                                }

                                receivedRelays.add(response.relay)
                                currentBroadcast = currentBroadcast.withResult(response.relay, response.result)

                                _activeBroadcasts.update { list ->
                                    list.replacing(currentBroadcast)
                                }
                            }
                        }

                        // Relays that never answered: the connection error or held refusal if there was one, else a timeout
                        relaysToRetry.filter { it !in receivedRelays }.forEach { relay ->
                            currentBroadcast = currentBroadcast.withResult(relay, connectionErrors[relay] ?: RelayResult.Timeout)
                        }

                        currentBroadcast
                    }

                client.publish(event, relaysToRetry)

                resultCollector.await()
            }

        client.removeConnectionListener(subscription)
        resultChannel.close()

        // Update in active broadcasts
        _activeBroadcasts.update { list ->
            list.replacing(finalBroadcast)
        }

        Log.d(TAG) { "Retry complete for ${broadcast.id}: ${finalBroadcast.successCount}/${finalBroadcast.totalRelays} success" }

        return finalBroadcast
    }

    /** Dismisses [ids] from the banner; see [hiding]. */
    fun hide(ids: Set<String>) {
        _activeBroadcasts.update { it.hiding(ids) }
    }

    private data class RelayResponse(
        val relay: NormalizedRelayUrl,
        val result: RelayResult,
        /** From the connection, not the relay: provisional until the relay sends its OK. */
        val connectionError: Boolean = false,
        /**
         * A `rate-limited:` refusal's NIP-01 back-off. The client resends the event once it
         * passes, so the refusal is provisional too, while that retry can still land in time.
         */
        val retryAfterMs: Long? = null,
    ) {
        /** True when this is a back-off that ends before the broadcast [started] times out. */
        fun backsOffWithin(started: TimeMark): Boolean {
            val backOff = retryAfterMs ?: return false
            return started.elapsedNow().inWholeMilliseconds + backOff < TIMEOUT_SECONDS * 1000
        }

        companion object {
            fun of(
                relay: NormalizedRelayUrl,
                msg: OkMessage,
            ) = if (msg.success) {
                RelayResponse(relay, RelayResult.Success)
            } else {
                RelayResponse(relay, RelayResult.Error(msg.message), retryAfterMs = msg.retryAfterMs)
            }
        }
    }
}
