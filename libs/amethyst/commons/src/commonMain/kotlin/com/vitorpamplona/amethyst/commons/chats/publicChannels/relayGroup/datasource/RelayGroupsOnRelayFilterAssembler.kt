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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupDirectoryFilter
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/** One screen's request for the full channel directory of a single relay. */
class RelayGroupsOnRelayQueryState(
    val relay: NormalizedRelayUrl,
    override val account: Account,
) : AccountScopedQuery

/**
 * Subscribes to the relay-signed directory (kinds 39000-39003) of a single relay,
 * so the "browse a relay's channels" screen sees every group the relay hosts. The
 * relay signs these with its own key; a single-relay, unscoped-by-`d` query pulls
 * the whole directory. Consumed into per-group [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel]s
 * keyed by (relay + group id).
 */
class RelayGroupsOnRelayFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupsOnRelayQueryState>() {
    val group =
        listOf(
            RelayGroupsOnRelaySubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupsOnRelaySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupsOnRelayQueryState>,
) : PerUniqueIdEoseManager<RelayGroupsOnRelayQueryState, NormalizedRelayUrl>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupsOnRelayQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = listOf(buildRelayGroupDirectoryFilter(key.relay, since?.get(key.relay)?.time))

    override fun id(key: RelayGroupsOnRelayQueryState) = key.relay
}
