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
package com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsConversationKinds
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds1
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds2
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds3
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtagAlts

/** What a feed with no kinds chosen asks for: everything Home shows for people and for hashtags. */
private val CustomFeedDefaultKinds: List<Int> =
    (HomePostsNewThreadKinds1 + HomePostsNewThreadKinds2 + HomePostsNewThreadKinds3 + HomePostsConversationKinds + HomePostsBuHashtagsKinds).distinct()

/**
 * One REQ per relay for a custom feed: its kinds (or every kind Home shows), its authors
 * on that relay, and its hashtags, all in the same filter so the relay applies them together.
 */
fun filterHomePostsByCustomFeed(
    feedSet: CustomFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    feedSet.set.map { (relay, scope) ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.HOME_FEED,
                    kinds = scope.kinds.ifEmpty { CustomFeedDefaultKinds }.sorted(),
                    authors = scope.authors.ifEmpty { null }?.sorted(),
                    tags = scope.hashtags.ifEmpty { null }?.let { mapOf("t" to hashtagAlts(it).sorted()) },
                    limit = 200,
                    since = since?.get(relay)?.time ?: defaultSince,
                ),
        )
    }
