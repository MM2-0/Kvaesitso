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

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/** One threads-screen's request for a single group's kind-11 threads. */
class RelayGroupOpenThreadsQueryState(
    val channel: RelayGroupChannel,
)

/**
 * The forum content of a NIP-29 group: kind-11 thread roots plus their kind-1111
 * comment trees, both scoped by the group's `h` tag and pinned to the host relay.
 * Active only while a group's Threads screen is open (threads are secondary to the
 * kind-9 chat, so we don't pay for them until asked). Fetching the comments here
 * too means opening a thread from the list has its replies already cached.
 */
class RelayGroupOpenThreadsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupOpenThreadsQueryState>() {
    val group =
        listOf(
            RelayGroupOpenThreadsSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupOpenThreadsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupOpenThreadsQueryState>,
) : PerUniqueIdEoseManager<RelayGroupOpenThreadsQueryState, GroupId>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupOpenThreadsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val groupId = key.channel.groupId
        return listOf(buildRelayGroupThreadsFilter(groupId, since?.get(groupId.relayUrl)?.time))
    }

    override fun id(key: RelayGroupOpenThreadsQueryState) = key.channel.groupId
}
