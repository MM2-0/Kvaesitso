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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Two relay sets, two distinct subscriptions:
 *
 * - [contentFetches] — for each user-configured content relay, the ids/addresses
 *   we want to pull (the actual notes the DVM curated).
 * - [listenRelays] — the union of DVM publish relays across all active DVMs
 *   (where they will deliver future kind 6300 / 7000 events for their requests).
 * - [requestIds] — the set of currently-active kind-5300 request ids to listen
 *   for. A single feed carries one; the merged "All favorite algo feeds"
 *   filter carries one per favorite algo feed.
 */
class FavoriteAlgoFeedTopNavPerRelayFilterSet(
    val contentFetches: Map<NormalizedRelayUrl, FavoriteAlgoFeedTopNavPerRelayFilter>,
    val listenRelays: Set<NormalizedRelayUrl>,
    val requestIds: Set<HexKey>,
) : IFeedTopNavPerRelayFilterSet {
    // Only the content half is per-relay. [listenRelays] is where the DVMs answer, which is a
    // delivery address rather than a scope, so a filter aimed there carries none.
    override fun scopeFor(relay: NormalizedRelayUrl) = contentFetches[relay]

    override fun relays() = contentFetches.keys
}
