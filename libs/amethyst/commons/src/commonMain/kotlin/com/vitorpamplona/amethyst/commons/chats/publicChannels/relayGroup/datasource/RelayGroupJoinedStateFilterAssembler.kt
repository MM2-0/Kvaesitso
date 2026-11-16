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
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupStateFilters
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/** One request to keep the relay-signed state of the user's joined groups live. */
class RelayGroupJoinedStateQueryState(
    override val account: Account,
) : AccountScopedQuery

/**
 * Keeps the relay-signed **state** of every group the user has joined live and current — name, picture,
 * about, admin/member rosters, roles and pins. Because these are small replaceable events, this is a
 * single **always-on** account subscription (mounted at `LoggedInPage`, gated on the NIP-29 chat toggle),
 * not a per-screen fetch: the cache always reflects the latest state, so no screen — Messages preview,
 * open chat, discovery card — ever has to re-query metadata. One `#d`-scoped filter per host relay carries
 * every joined group id on it; `since` is the shared per-relay EOSE (fine for replaceable events — a
 * reconnect just re-confirms).
 *
 * Membership, pending→member transitions and roster counts therefore stay accurate everywhere without
 * opening each chat — the reason the old `RelayGroupMyJoinedGroups` roster path existed, promoted from
 * "while a groups screen is up" to genuinely always-on.
 */
class RelayGroupJoinedStateFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupJoinedStateQueryState>() {
    val group = listOf(RelayGroupJoinedStateSubAssembler(client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupJoinedStateSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupJoinedStateQueryState>,
) : PerUniqueIdEoseManager<RelayGroupJoinedStateQueryState, Account>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupJoinedStateQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP29)) return null
        val joined = key.account.relayGroupList.liveRelayGroupList.value
        if (joined.isEmpty()) return null

        return buildRelayGroupStateFilters(joined) { since?.get(it)?.time }
    }

    override fun id(key: RelayGroupJoinedStateQueryState) = key.account

    override val watchedChatFeeds = setOf(ChatFeedType.NIP29)
}
