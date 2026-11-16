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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

interface IFeedTopNavPerRelayFilterSet {
    /**
     * What this feed selection asks of one relay — the follows, hashtags, geohashes or communities
     * scoped to it — or null when the set says nothing per relay.
     *
     * Declared here so an `ExplainedFilter` can carry the scope that produced it. A discovery filter
     * names no entity (it searches for things rather than serving known ones), which used to leave
     * the Active Subscriptions screen with a nameless row it could only label "no entity". The
     * selection behind it was always known at build time; it just had nowhere to travel. Returning
     * the per-relay value rather than the whole set keeps it exact: the filter is already per relay,
     * so it carries only the slice that applies to it.
     */
    fun scopeFor(relay: NormalizedRelayUrl): IFeedTopNavPerRelayFilter?

    /**
     * The relays this selection's subscriptions should be issued on, regardless of whether the
     * selection's per-kind filter dispatch produces a filter for each of them.
     *
     * Consumers that must stay alive across every selection — the DVM heartbeat REQ, whose beats
     * keep the cached discovery list's liveness gate fresh — read this instead of deriving relays
     * from the per-kind filters, which silently skip relays whose per-relay slice is empty and
     * whole selections (the Relay variant) that dispatch no content filters at all.
     */
    fun relays(): Set<NormalizedRelayUrl>
}
