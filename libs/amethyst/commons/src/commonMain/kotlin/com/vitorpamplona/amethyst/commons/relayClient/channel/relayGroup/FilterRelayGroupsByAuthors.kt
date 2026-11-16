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
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/** One scan of the group cache, grouped by host relay — reused across every relay in a filter set. */
fun relayGroupChannelsByRelay(cache: ICacheProvider): Map<NormalizedRelayUrl, List<RelayGroupChannel>> = cache.allRelayGroupChannels().groupBy { it.groupId.relayUrl }

/**
 * A group's kind-39000 is relay-signed, so a follow shows up in three distinct ways, and each is a
 * real relay-side REQ (not a broad "pull everything" pass):
 *  1. the follow IS the relay signing key → `{kinds:[39000], authors:<follows>}` (metadata included);
 *  2. a follow is an admin/member → `{kinds:[39001,39002], #p:<follows>}` (standard p-tag filter);
 *  3. metadata backfill for the groups (2) surfaced → `{kinds:[39000], #d:<their group-ids>}`, read
 *     from the rosters already in [cachedChannels] (empty on the first pass, filled once (2)'s events
 *     land and the sub-assembler re-invalidates).
 *
 * [cachedChannels] is this relay's slice of [relayGroupChannelsByRelay]; the set-level callers scan
 * the cache once and pass the slice so the (3) backfill isn't an O(relays × total-groups) re-scan.
 */
fun filterRelayGroupsByAuthors(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
    cachedChannels: List<RelayGroupChannel>,
): List<RelayBasedFilter> {
    if (authors.isEmpty()) return emptyList()
    val authorList = authors.sorted()

    val filters =
        mutableListOf(
            // (1) groups whose relay signing-key is a follow
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.RELAY_GROUPS,
                        authors = authorList,
                        kinds = listOf(GroupMetadataEvent.KIND),
                        limit = 200,
                        since = since,
                    ),
            ),
            // (2) groups where a follow is an admin (39001) or member (39002)
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.RELAY_GROUPS,
                        kinds = listOf(GroupAdminsEvent.KIND, GroupMembersEvent.KIND),
                        tags = mapOf(PTag.TAG_NAME to authorList),
                        limit = 200,
                        since = since,
                    ),
            ),
        )

    // (3) backfill 39000 metadata for the roster-discovered groups already known on this relay.
    val rosterGroupIds =
        cachedChannels
            .filter { channel -> channel.admins.any { it.pubKey in authors } || channel.members.any { it in authors } }
            .map { it.groupId.id }
    if (rosterGroupIds.isNotEmpty()) {
        filters +=
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.RELAY_GROUPS,
                        kinds = listOf(GroupMetadataEvent.KIND),
                        tags = mapOf(DTag.TAG_NAME to rosterGroupIds),
                        limit = 200,
                        since = since,
                    ),
            )
    }

    return filters
}

fun filterRelayGroupsByAuthors(
    cache: ICacheProvider,
    authorSet: AuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    val channelsByRelay = relayGroupChannelsByRelay(cache)
    return authorSet.set.flatMap {
        filterRelayGroupsByAuthors(
            relay = it.key,
            authors = it.value.authors,
            since = since?.get(it.key)?.time ?: defaultSince,
            cachedChannels = channelsByRelay[it.key].orEmpty(),
        )
    }
}

fun filterRelayGroupsByMutedAuthors(
    cache: ICacheProvider,
    authorSet: MutedAuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    val channelsByRelay = relayGroupChannelsByRelay(cache)
    return authorSet.set.flatMap {
        filterRelayGroupsByAuthors(
            relay = it.key,
            authors = it.value.authors,
            since = since?.get(it.key)?.time ?: defaultSince,
            cachedChannels = channelsByRelay[it.key].orEmpty(),
        )
    }
}
