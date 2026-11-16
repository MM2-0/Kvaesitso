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

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsConversationKinds
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds1
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds2
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds3
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun filterHomePostsByGlobal(
    relays: GlobalTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    newThreadSince: Long?,
    repliesSince: Long?,
): List<RelayBasedFilter> {
    if (relays.set.isEmpty()) return emptyList()

    return relays.set.flatMap {
        val since = since?.get(it.key)?.time
        val relayUrl = it.key
        listOf(
            RelayBasedFilter(
                relay = relayUrl,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.HOME_FEED,
                        kinds = HomePostsNewThreadKinds1,
                        limit = 50,
                        since = since ?: newThreadSince,
                    ),
            ),
            RelayBasedFilter(
                relay = relayUrl,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.HOME_FEED,
                        kinds = HomePostsNewThreadKinds2,
                        limit = 5,
                        since = since ?: newThreadSince,
                    ),
            ),
            RelayBasedFilter(
                relay = relayUrl,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.HOME_FEED,
                        kinds = HomePostsNewThreadKinds3,
                        limit = 20,
                        since = since ?: newThreadSince,
                    ),
            ),
            RelayBasedFilter(
                relay = relayUrl,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.HOME_FEED,
                        kinds = HomePostsConversationKinds,
                        limit = 50,
                        since = since ?: repliesSince,
                    ),
            ),
        )
    }
}
