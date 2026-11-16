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
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupAuxFilter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupOpenChatTailFilter
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

/** One open NIP-29 group whose recent chat the screen wants live. */
class RelayGroupOpenChatTailQueryState(
    override val account: Account,
    val groupId: GroupId,
) : AccountScopedQuery

/**
 * Per-open-group **live tail**: the recent chat window of the *currently open* group, `#h`-scoped on
 * its host relay, `since = recentBoundary()`. The group analog of the NIP-04 per-conversation live tail.
 *
 * The batched [RelayGroupJoinedChatTailFilterAssembler] already covers every *joined* group; this exists so
 * an open **non-joined** group (opened by link before joining, not in `liveRelayGroupList`) still gets
 * its recent messages and live updates. For a joined open group it simply overlaps the batched tail
 * (harmless — events dedup by id on ingest). Older history is the [RelayGroupOpenChatHistorySubAssembler]'s job.
 */
class RelayGroupOpenChatTailFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupOpenChatTailQueryState>() {
    val tail = RelayGroupOpenChatTailSubAssembler(client, ::allKeys)

    val group = listOf(tail)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupOpenChatTailSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupOpenChatTailQueryState>,
) : PerUniqueIdEoseManager<RelayGroupOpenChatTailQueryState, GroupId>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("relayGroup.chat.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: RelayGroupOpenChatTailQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        windowLoad.setExpectedRelays(setOf(key.groupId.relayUrl))
        return listOf(
            buildRelayGroupOpenChatTailFilter(key.groupId, DmHistoryTuning.recentBoundary()),
            // Reactions/deletions for every message on screen, `#h`-scoped rather than `#e`-scoped —
            // see [RELAY_GROUP_AUX_KINDS]. Covers a non-joined group opened by link too.
            buildRelayGroupAuxFilter(key.groupId, DmHistoryTuning.recentBoundary()),
        )
    }

    override fun id(key: RelayGroupOpenChatTailQueryState) = key.groupId

    override fun newSub(key: RelayGroupOpenChatTailQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay: NormalizedRelayUrl, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
