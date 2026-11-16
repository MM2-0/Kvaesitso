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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Platform-agnostic interface between [OutboxDispatcher] and the platform's
 * event cache. Desktop and `amy` each provide their own implementation —
 * DesktopLocalCache on the app side, a minimal in-memory adapter over the
 * amy local store on the CLI side.
 *
 * The dispatcher only needs three capabilities:
 *
 *   1. Peek at what kind-10002 events are already stored so it can skip
 *      Phase-1 discovery for authors whose write-relay list is already
 *      known (from hydration or a previous session's fetch).
 *   2. Ingest a kind-10002 that just came back from an index relay so
 *      subsequent lookups don't re-fetch it.
 *   3. Ingest a kind-0 or kind-3 that just came back from an outbox
 *      relay so the platform cache/UI can pick it up through the usual
 *      consume path.
 *
 * Every method must be idempotent — the dispatcher may re-fire the same
 * event through the gateway if two relays happen to return the same
 * addressable event.
 */
interface OutboxCacheGateway {
    /**
     * Returns the currently-cached kind-10002 event for [pubkey], or null
     * if the platform cache doesn't have one yet.
     */
    fun cachedOutbox(pubkey: HexKey): AdvertisedRelayListEvent?

    /**
     * Called for every kind-10002 the dispatcher receives during Phase 1.
     * The gateway should route it through its normal consume path so the
     * event is stored, deduped by createdAt, and picked up by any state
     * holders observing the addressable-notes cache.
     */
    fun onOutboxDiscovered(
        event: AdvertisedRelayListEvent,
        relay: NormalizedRelayUrl,
    )

    /**
     * Called for every kind-0 (metadata) or kind-3 (contact list) the
     * dispatcher receives during Phase 2 or Phase 3. The gateway should
     * route it through its normal consume path — this is how new profile
     * metadata and follow lists reach downstream consumers like the WoT
     * service and the UI.
     */
    fun onDiscoveredEvent(
        event: Event,
        relay: NormalizedRelayUrl,
    )
}
