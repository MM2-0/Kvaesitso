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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.coroutines.CoroutineScope

/**
 * Measures how fast each relay answers (OK for a post, EOSE and the first result for a query) and
 * which relays are slow next to the others, for the relay screens. One per app, over its relay
 * client: the tracker listens to every relay the client talks to, and [store] publishes the p50s
 * and the slow-relay verdicts once a minute.
 */
class RelayLatencyMonitor(
    private val client: INostrClient,
    persistence: RelayHealthPersistence,
    scope: CoroutineScope,
    /** Whether the client reaches this relay through Tor; those relays are not compared. */
    isTorRouted: (NormalizedRelayUrl) -> Boolean,
    /** The relay's cached NIP-11 document, if any: auth- or payment-required relays are not compared. */
    nip11: (NormalizedRelayUrl) -> Nip11RelayInformation?,
) {
    private val tracker = RelayLatencyTracker()

    val store: RelayHealthStore =
        RelayHealthStore(
            persistence = persistence,
            parentScope = scope,
            latencyTracker = tracker,
            nip11ForRelay = nip11,
            // Anonymous queries to an auth- or payment-required relay mostly time out or close:
            // counting them would mark those relays slow for no fault of theirs.
            authProvider = { false },
            torRoutedProvider = isTorRouted,
        )

    private val healthListener = RelayHealthListener(store)
    private val latencyListener = RelayLatencyListener(tracker)

    init {
        healthListener.installInto(client)
        latencyListener.installInto(client)
    }

    fun close() {
        healthListener.uninstallFrom(client)
        latencyListener.uninstallFrom(client)
        store.close()
    }
}
