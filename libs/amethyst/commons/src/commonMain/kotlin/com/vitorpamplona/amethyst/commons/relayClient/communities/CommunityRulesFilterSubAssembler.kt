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

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent

/**
 * Subscribes to the latest NIP-9B [CommunityRulesEvent] (kind:34551) for each
 * tracked community.
 *
 * Reuses the existing [CommunityQueryState] keyspace so any screen that already
 * subscribes to the community feed (via [CommunityFilterAssemblerSubscription])
 * also pulls the rules document. Only events signed by the community owner or a
 * declared moderator are accepted, matching the trust model of NIP-72 approvals.
 */
class CommunityRulesFilterSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<CommunityQueryState>,
) : SingleSubEoseManager<CommunityQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<CommunityQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        return keys.flatMap { key ->
            val commEvent = key.community.event
            if (commEvent !is CommunityDefinitionEvent) return@flatMap emptyList()

            val signers = commEvent.moderatorKeys()
            val communityRelays =
                (
                    commEvent.relayUrls().ifEmpty {
                        cache.relayHints.hintsForAddress(commEvent.addressTag())
                    } + key.community.relayUrls()
                ).toSet()

            communityRelays.map { relay ->
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.COMMUNITY_FEED,
                            kinds = listOf(CommunityRulesEvent.KIND),
                            authors = signers.sorted(),
                            tags = mapOf("a" to listOf(commEvent.addressTag())),
                            limit = 5,
                            since = since?.get(relay)?.time,
                        ),
                )
            }
        }
    }

    override fun distinct(key: CommunityQueryState) = key.community.idHex
}
