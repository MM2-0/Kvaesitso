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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterGroupNotificationsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupAuxFilter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupJoinedChatTailFilter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupPreviewFilter
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.amethyst.commons.relayClient.paging.trackingListener
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/** One Buzz DM channel whose recent chat is kept live. */
class BuzzDmJoinedChatTailQueryState(
    override val account: Account,
    val groupId: GroupId,
) : AccountScopedQuery

/**
 * Always-on **live tail** for the recent chat of every Buzz DM channel the viewer belongs to — the DM
 * analog of [RelayGroupJoinedChatTailFilterAssembler]. A Buzz DM is a relay-authoritative NIP-29 group
 * (UUID `h`), so its messages ride the exact same `#h`-scoped batched tail; only the channel *source*
 * differs — [BuzzDmChannels] (populated by
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzDmDiscoveryPreload]) rather than the published
 * kind-10009 group list, because DM memberships are server-side and deliberately never published.
 *
 * Keeping these warm app-wide is what lets a Buzz DM surface on the Notifications tab and in push without
 * the viewer opening the conversation. Hidden DMs (per the 30622 snapshot in [BuzzDmRegistry]) are
 * excluded so a hidden conversation neither streams nor notifies.
 */
class BuzzDmJoinedChatTailFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<BuzzDmJoinedChatTailQueryState>() {
    val tail = BuzzDmJoinedChatTailSubAssembler(client, ::allKeys)

    val group = listOf(tail)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class BuzzDmJoinedChatTailSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<BuzzDmJoinedChatTailQueryState>,
) : PerUniqueIdEoseManager<BuzzDmJoinedChatTailQueryState, GroupId>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("buzzDm.preview.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: BuzzDmJoinedChatTailQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        // The preload only mounts visible channels; re-checked here so hiding a conversation
        // empties its subscription even if the key outlives the recomposition.
        val me = key.account.userProfile().pubkeyHex
        if (key.groupId.id in BuzzDmRegistry.hiddenFor(me)) return null

        // Same one-subscription-per-channel shape as the joined-group tail: a Buzz DM is a
        // relay-authoritative NIP-29 group, so batching ids into one `#h` would register the
        // subscription as global on buzz and never deliver live messages. See
        // [buildRelayGroupJoinedChatTailFilter]. The second filter carries the DM's activity
        // addressed to me (reactions/zaps/reports) — same channel, so the subscription stays scoped.
        val relay = key.groupId.relayUrl
        windowLoad.setExpectedRelays(setOf(relay))
        return listOf(
            buildRelayGroupJoinedChatTailFilter(key.groupId, DmHistoryTuning.recentBoundary()),
            // Newest message at any age — a DM conversation dormant for over a week must still show its
            // last message on Messages, not a placeholder. See [buildRelayGroupPreviewFilter].
            buildRelayGroupPreviewFilter(key.groupId, since?.get(relay)?.time),
            // A DM channel takes reactions/deletions the same way a group channel does.
            buildRelayGroupAuxFilter(key.groupId, DmHistoryTuning.recentBoundary()),
        ) +
            filterGroupNotificationsToPubkey(
                relay = relay,
                pubkey = me,
                groupIds = listOf(key.groupId.id),
                since = since?.get(relay)?.time,
            )
    }

    override fun id(key: BuzzDmJoinedChatTailQueryState) = key.groupId

    override fun newSub(key: BuzzDmJoinedChatTailQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay: NormalizedRelayUrl, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
