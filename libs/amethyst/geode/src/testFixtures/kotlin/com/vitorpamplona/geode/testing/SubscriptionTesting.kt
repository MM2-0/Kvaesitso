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
package com.vitorpamplona.geode.testing

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeout

/** Tagged result of [collectUntilEose]: stored events plus whether EOSE actually arrived. */
data class CollectResult(
    val events: List<Event>,
    val eoseReceived: Boolean,
)

/**
 * Subscribe with [filter] on a single [relay], drain the
 * historical-replay phase, and return when EOSE arrives. The
 * subscription is closed before this returns. The pattern that 80% of
 * REQ-style tests need.
 *
 * ```
 * val (events, eose) = client.collectUntilEose(defaultRelayUrl, Filter(kinds = listOf(1)))
 * assertEquals(20, events.size)
 * assertTrue(eose)
 * ```
 *
 * @param timeoutMillis time to wait for EOSE before failing the test.
 *        Default 5 s — generous for in-process; tighten if needed.
 */
suspend fun NostrClient.collectUntilEose(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMillis: Long = 5_000,
): CollectResult = collectUntilEoseMulti(relay, listOf(filter), timeoutMillis)

/**
 * Multi-filter variant. NIP-01 allows a REQ to carry several filters
 * that the relay OR's together. EOSE fires once after the union of all
 * filters has been replayed.
 */
suspend fun NostrClient.collectUntilEoseMulti(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    timeoutMillis: Long = 5_000,
): CollectResult {
    val ch = Channel<Signal>(UNLIMITED)
    val subId = "test-sub-${nextSubId()}"
    subscribe(
        subId,
        mapOf(relay to filters),
        object : SubscriptionListener {
            override suspend fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                ch.trySend(Signal.Ev(event))
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                ch.trySend(Signal.Eose)
            }
        },
    )

    val events = mutableListOf<Event>()
    var eose = false
    try {
        withTimeout(timeoutMillis) {
            while (!eose) {
                when (val msg = ch.receive()) {
                    is Signal.Ev -> events += msg.event
                    Signal.Eose -> eose = true
                }
            }
        }
    } finally {
        unsubscribe(subId)
    }
    return CollectResult(events, eose)
}

private sealed interface Signal {
    data class Ev(
        val event: Event,
    ) : Signal

    object Eose : Signal
}

/** Monotonic counter for unique sub-ids inside a JVM. */
private var subIdSeq: Int = 0

private fun nextSubId(): Int = ++subIdSeq
