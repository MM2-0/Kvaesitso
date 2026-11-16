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
package com.vitorpamplona.amethyst.commons.relayClient.communities

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.mapOfSet

class CommunityFeedFilterSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<CommunityQueryState>,
) : SingleSubEoseManager<CommunityQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<CommunityQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        val plans = keys.mapNotNull { plan(it) }

        // One cursor per relay is shared by every community opened on it.
        val relaySince =
            sinceDroppingGrownRelays(
                mapOfSet {
                    plans.forEach { plan ->
                        (plan.communityRelays + plan.moderatorsOutbox.keys).forEach { add(it, plan.community.idHex) }
                    }
                },
                since,
            )

        return plans.flatMap { plan ->
            plan.moderatorsOutbox.map { (relay, authors) ->
                filterCommunityPostsFromModerators(relay, authors, plan.event, relaySince?.get(relay)?.time)
            } +
                plan.communityRelays.map { relay ->
                    filterCommunityPostsFromEverybody(relay, plan.event, relaySince?.get(relay)?.time)
                }
        }
    }

    private class Plan(
        val community: AddressableNote,
        val event: CommunityDefinitionEvent,
        val communityRelays: Set<NormalizedRelayUrl>,
        val moderatorsOutbox: Map<NormalizedRelayUrl, Set<String>>,
    )

    private fun plan(key: CommunityQueryState): Plan? {
        val commEvent = key.community.event as? CommunityDefinitionEvent ?: return null
        val communityRelays =
            (
                commEvent.relayUrls().ifEmpty {
                    cache.relayHints.hintsForAddress(commEvent.addressTag())
                } + key.community.relayUrls()
            ).toSet()

        val moderatorsOutbox =
            mapOfSet {
                commEvent.moderatorKeys().forEach { modKey ->
                    // hits the relay of each moderator for approvals
                    cache.checkGetOrCreateUser(modKey)?.outboxRelays()?.forEach { relay ->
                        add(relay, modKey)
                    }

                    // static relay hints from this community
                    communityRelays.forEach { relay ->
                        add(relay, modKey)
                    }
                }
            }

        return Plan(key.community, commEvent, communityRelays, moderatorsOutbox)
    }

    override fun distinct(key: CommunityQueryState) = key.community.idHex
}
