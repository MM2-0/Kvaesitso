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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * A single, central enforcement point for the NIP-51 kind:10006 blocked relay list.
 *
 * Relay targeting in this app is fully distributed: every feed, loader, finder, and
 * publish path builds its own `Map<relay, filters>` (for REQ/COUNT) or relay set (for
 * publish) and hands it to [INostrClient]. Only a subset of those sites remembered to
 * subtract the blocked list, so blocked relays still leaked in through event/thread
 * loaders, the user-metadata finder, DM targeting, and the broadcast/publish path.
 *
 * Rather than patch every selection site (and re-open the same gap with every new
 * assembler), this decorator wraps the real client and strips blocked relays from the
 * three operations that open a socket — [subscribe], [count] and [publish] — right
 * before they reach the pool. Because the one-shot fetch helpers ([fetchAll],
 * [fetchFirst], [fetchAllPages], NIP-45 count, …) are extension functions that route
 * through [subscribe]/[count], wrapping the client covers them for free.
 *
 * [blockedRelays] is read on every call so the currently-active account's list always
 * applies, with no caching to invalidate on account switch.
 *
 * Note: the NIP-77 negentropy paths drive a single socket directly through
 * [getOrCreateRelay] with a caller-chosen relay; that escape hatch is delegated
 * unchanged, so callers that use it must keep filtering blocked relays themselves.
 */
class BlockedRelayFilteringClient(
    private val delegate: INostrClient,
    private val blockedRelays: () -> Set<NormalizedRelayUrl>,
) : INostrClient by delegate {
    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {
        delegate.subscribe(subId, filters.withoutBlocked(), listener)
    }

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        delegate.count(subId, filters.withoutBlocked())
    }

    override fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        val blocked = blockedRelays()
        delegate.publish(event, if (blocked.hitsNoneOf(relayList)) relayList else relayList - blocked)
    }

    // Overridden, not delegated: `by delegate` would hand the delegate the full relay list.
    override fun publishRaw(
        event: Event,
        eventJson: String,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        val blocked = blockedRelays()
        delegate.publishRaw(event, eventJson, if (blocked.hitsNoneOf(relayList)) relayList else relayList - blocked)
    }

    private fun Map<NormalizedRelayUrl, List<Filter>>.withoutBlocked(): Map<NormalizedRelayUrl, List<Filter>> {
        val blocked = blockedRelays()
        if (blocked.hitsNoneOf(keys)) return this
        return filterKeys { it !in blocked }
    }

    /**
     * Whether none of the blocked relays appear in [targets] — i.e. whether the caller can be
     * handed its own collection back untouched.
     *
     * Worth the extra scan because this runs on every REQ, COUNT and publish (filter assemblers
     * rebuild their per-relay targets constantly) while a blocked relay is, by definition, one the
     * app has stopped aiming at — so "the block list is non-empty but irrelevant to this call" is
     * the overwhelmingly common case. Without the check, `filterKeys` / `minus` allocate and copy
     * the whole collection every time just to reproduce it unchanged. A hash lookup per target and
     * no allocation beats an allocation plus a full copy.
     */
    private fun Set<NormalizedRelayUrl>.hitsNoneOf(targets: Collection<NormalizedRelayUrl>): Boolean = isEmpty() || targets.none { it in this }
}
