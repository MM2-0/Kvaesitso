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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource

import com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/** One screen's request to keep the user's joined Concord Channels live. */
class ConcordChannelQueryState(
    override val account: Account,
) : AccountScopedQuery

/**
 * Keeps every joined Concord community's planes live while a Concord-bearing
 * screen is on top — the Concord analog of `RelayGroupMyJoinedGroupsFilterAssembler`.
 *
 * Unlike NIP-29, a Concord plane wrap's `p` tag is ephemeral, so there is no
 * `#p=me` subscription: each plane is fetched by its derived stream address
 * (`authors=[planePk]`, kind 1059). Every joined community's Control Plane is
 * subscribed upfront; once a Control Plane folds, [Account.concordSessions] bumps
 * its revision and this assembler re-derives to also watch each channel's Chat
 * Plane (see [ConcordChannelSubscription]).
 */
class ConcordChannelFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<ConcordChannelQueryState>() {
    val group =
        listOf(
            ConcordChannelSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class ConcordChannelSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ConcordChannelQueryState>,
) : PerUniqueIdEoseManager<ConcordChannelQueryState, Account>(client, allKeys) {
    override fun updateFilter(
        key: ConcordChannelQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val account = key.account
        if (!account.chatFeedToggles.isEnabled(ChatFeedType.CONCORD)) return null
        val entries = account.concordChannelList.liveCommunities.value
        if (entries.isEmpty()) return null

        // The Control Plane rides its OWN filter, kept apart from the Guestbook / rekey / channel
        // planes, so a chatty Guestbook can't crowd its channel-defining editions out of a relay's
        // per-filter result cap (the "Soapbox shows 1 of 12 channels" bug). See
        // [ConcordSubscriptionPlanner.controlIsolatedFilters].
        val all =
            ConcordSubscriptionPlanner.controlIsolatedFilters(
                entries,
                since = since,
                accountPubKey = account.userProfile().pubkeyHex,
            ) { entry ->
                account.concordSessions
                    .sessionFor(entry.id)
                    ?.state
                    ?.value
            }
        return all.ifEmpty { null }
    }

    override fun id(key: ConcordChannelQueryState) = key.account

    override val watchedChatFeeds = setOf(ChatFeedType.CONCORD)
}
