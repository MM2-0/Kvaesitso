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
package com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

// The All-Follows filter is a big-OR: a group surfaces if a follow is its relay key / admin /
// member (via [filterRelayGroupsByAuthors]) OR it carries a followed topic / geohash. The local
// AnyOf constraint matches all three lenses, so the REQ must fetch all three — otherwise a group
// under a followed hashtag run by non-followed people would be matched but never requested.
fun filterRelayGroupsByFollows(
    cache: ICacheProvider,
    followsSet: AllFollowsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (followsSet.set.isEmpty()) return emptyList()

    val channelsByRelay = relayGroupChannelsByRelay(cache)
    return followsSet.set.flatMap {
        val relay = it.key
        val relaySince = since?.get(relay)?.time ?: defaultSince
        buildList {
            it.value.authors?.takeIf { a -> a.isNotEmpty() }?.let { authors ->
                addAll(filterRelayGroupsByAuthors(relay, authors, relaySince, channelsByRelay[relay].orEmpty()))
            }
            it.value.hashtags?.takeIf { h -> h.isNotEmpty() }?.let { hashtags ->
                addAll(filterRelayGroupsByHashtag(relay, hashtags, relaySince))
            }
            it.value.geotags?.takeIf { g -> g.isNotEmpty() }?.let { geotags ->
                addAll(filterRelayGroupsByGeohashes(relay, geotags, relaySince))
            }
        }
    }
}
