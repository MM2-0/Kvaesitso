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
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupHistoryFilters
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/** One open NIP-29 group whose older history the chat screen wants paged in. */
class RelayGroupOpenChatHistoryQueryState(
    override val account: Account,
    val groupId: GroupId,
) : AccountScopedQuery

/**
 * Mounts the on-demand **history** pager for whichever NIP-29 group chat screen is open. The live
 * tail ([RelayGroupOpenChatTailFilterAssembler]) holds the recent window each host relay serves; this
 * pages older kind-9/poll messages backward by `until`+`limit` on the group's host relay, exactly
 * like the per-conversation NIP-04 history and the Concord channel history
 * ([com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelHistoryFilterAssembler]).
 */
class RelayGroupOpenChatHistoryFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupOpenChatHistoryQueryState>() {
    val history = RelayGroupOpenChatHistorySubAssembler(client, ::allKeys)

    val group = listOf(history)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

/**
 * Pages one group's older chat by `until`+`limit`, on the single host relay, on demand. The per-relay
 * cursors live on the group's [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel]
 * (so reopening keeps progress); this binds the single-active [BackwardRelayPager] to the open group on
 * [newSub], builds the `#h`-scoped REQ at the relay's requested cursor, and forwards relay callbacks in.
 * Landing happens on the normal ingest path (host echo → `attachToRelayGroupIfScoped`); the pager only
 * needs each event's `createdAt`. All authors (never author-filtered), so it also re-materializes the
 * user's own older sent messages — the job the retired `filterMyMessagesToRelayGroup` used to do.
 */
class RelayGroupOpenChatHistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupOpenChatHistoryQueryState>,
) : PerUniqueIdEoseManager<RelayGroupOpenChatHistoryQueryState, GroupId>(client, allKeys) {
    private val pager = BackwardRelayPager("relayGroup.chat.history")

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    override fun id(key: RelayGroupOpenChatHistoryQueryState) = key.groupId

    // This group's persistent paging cursors, held on its LocalCache RelayGroupChannel.
    private fun cursorsFor(key: RelayGroupOpenChatHistoryQueryState) = LocalCache.getOrCreateRelayGroupChannel(key.groupId).history

    /** A relay group lives on exactly one relay: its host. */
    private fun relaysFor(key: RelayGroupOpenChatHistoryQueryState): Set<NormalizedRelayUrl> = setOf(key.groupId.relayUrl)

    override fun updateFilter(
        key: RelayGroupOpenChatHistoryQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val relays = relaysFor(key)
        // Only armed (advanced, not done) relays carry a REQ, each at its own requested cursor. A parked
        // relay keeps no filter here, so re-assembly (a marker advancing) doesn't re-REQ a settled window.
        val armed = pager.armedRelays(relays)
        if (armed.isEmpty()) return emptyList()
        return buildRelayGroupHistoryFilters(key.groupId, armed, { pager.requestedUntilFor(it) }, pager.pageLimit)
    }

    /** Steps a single [relay] to its next, older page for the open group. Driven by its on-screen marker. */
    fun advance(relay: NormalizedRelayUrl) {
        if (pager.advance(relay)) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. For a group too short to scroll / eager backfill. */
    fun advanceAll() {
        if (pager.advanceAll()) invalidateFilters()
    }

    override fun endSub(
        key: GroupId,
        subId: String,
    ) {
        super.endSub(key, subId)
        // The subscription is gone: let go of what the app-wide pager holds for it.
        pager.unbind(key)
    }

    override fun newSub(key: RelayGroupOpenChatHistoryQueryState): Subscription {
        // Repoint the single-active orchestrator at this group's cursors and its host relay.
        pager.bind(cursorsFor(key), key.account.scope, owner = id(key)) { relaysFor(key) }
        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: RelayGroupOpenChatHistoryQueryState): SubscriptionListener {
        // A just-backgrounded group's subscription can still deliver after the orchestrator rebinds to
        // another group; gate the pager (single-active) on whether it's still bound to THIS group's
        // cursors so a late callback can't move another group's cursors. newEose runs regardless.
        val myCursors = cursorsFor(key)
        return object : SubscriptionListener {
            override suspend fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onEvent(relay, event.createdAt)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onEose(relay)
                newEose(key, relay, TimeUtils.now(), forFilters)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onClosed(relay, message)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onCannotConnect(relay, message)
            }
        }
    }
}
