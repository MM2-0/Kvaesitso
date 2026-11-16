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
package com.vitorpamplona.amethyst.commons.relayClient.video.nip65Follows

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core.filterPictureAndVideoGeohash
import com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core.filterPictureAndVideoHashtag
import com.vitorpamplona.amethyst.commons.relayClient.video.nip72Communities.filterPictureAndVideoAllCommunities
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun filterPictureAndVideoByFollows(
    followsSet: AllFollowsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (followsSet.set.isEmpty()) return emptyList()

    return followsSet.set.flatMap {
        val since = since?.get(it.key)?.time ?: defaultSince
        val relay = it.key

        listOfNotNull(
            it.value.authors?.let { authors ->
                filterPictureAndVideoAuthors(relay, authors, since)
            },
            it.value.geotags?.let { geotags ->
                filterPictureAndVideoGeohash(relay, geotags, since)
            },
            it.value.hashtags?.let { hashtags ->
                filterPictureAndVideoHashtag(relay, hashtags, since)
            },
            it.value.communities?.let { communities ->
                filterPictureAndVideoAllCommunities(relay, communities, since)
            },
        ).flatten()
    }
}
