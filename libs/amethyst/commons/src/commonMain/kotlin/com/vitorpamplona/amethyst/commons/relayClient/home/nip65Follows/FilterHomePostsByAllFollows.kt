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
package com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByGeohashes
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByHashtags
import com.vitorpamplona.amethyst.commons.relayClient.home.nip72Communities.filterHomePostsFromAllCommunities
import com.vitorpamplona.amethyst.commons.relayClient.nip22Comments.filterHomePostsByScopes
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun filterHomePostsByAllFollows(
    followsSet: AllFollowsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    sinceBoundaryNew: Long?,
    sinceBoundaryReply: Long?,
): List<RelayBasedFilter> {
    if (followsSet.set.isEmpty()) return emptyList()

    return followsSet.set.flatMap { (relay, filter) ->
        val since = since?.get(relay)?.time

        listOfNotNull(
            filter.authors?.let {
                filterNewHomePostsByAuthors(relay, it, since ?: sinceBoundaryNew)
            },
            filter.authors?.let {
                filterReplyHomePostsByAuthors(relay, it, since ?: sinceBoundaryReply)
            },
            filter.geotags?.let {
                filterHomePostsByGeohashes(relay, it, since ?: sinceBoundaryNew)
            },
            filter.geotagScopes?.let {
                filterHomePostsByScopes(relay, it, since ?: sinceBoundaryNew)
            },
            filter.hashtags?.let {
                filterHomePostsByHashtags(relay, it, since ?: sinceBoundaryNew)
            },
            filter.hashtagScopes?.let {
                filterHomePostsByScopes(relay, it, since ?: sinceBoundaryNew)
            },
            filter.communities?.let {
                filterHomePostsFromAllCommunities(relay, it, since ?: sinceBoundaryNew)
            },
        ).flatten()
    }
}
