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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/** One open Concord Channel whose older history the screen wants paged in. */
class ConcordChannelHistoryQueryState(
    override val account: Account,
    val communityId: String,
    val channelId: String,
) : AccountScopedQuery

/**
 * Mounts the on-demand **history** pager for whichever Concord Channel screen is open. The live
 * [ConcordChannelFilterAssembler] only holds the recent tail the relay serves for each channel plane;
 * this pages older messages backward by `until`+`limit` per relay, exactly like the NIP-04 per-
 * conversation history ([com.vitorpamplona.amethyst.commons.chats.privateDM.datasource.ChatroomNip04HistorySubAssembler]).
 */
class ConcordChannelHistoryFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<ConcordChannelHistoryQueryState>() {
    val history = ConcordChannelHistorySubAssembler(client, ::allKeys)

    val group = listOf(history)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

/**
 * Pages one Concord Channel's older wraps by `until`+`limit`, per relay, on demand. The per-relay
 * cursors live on the channel's [com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel] (so
 * reopening keeps progress); this binds the single-active [BackwardRelayPager] to the open channel on
 * [newSub], builds the kind-1059 channel-plane REQ per armed relay, and forwards relay callbacks into
 * the pager. Decryption + landing happen on the normal ingest path (the wraps flow through
 * `concordSessions.ingest`); the pager only needs each wrap's `createdAt`.
 */
class ConcordChannelHistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ConcordChannelHistoryQueryState>,
) : PerUniqueIdEoseManager<ConcordChannelHistoryQueryState, ConcordChannelId>(client, allKeys) {
    // Floor at `now` (liveTailSeconds = 0), NOT the DM 7-day tail: the Concord live subscription isn't a
    // strict recent-tail (it asks the plane author unbounded and the relay caps the result), so paging
    // must walk the WHOLE history from the top to reach "recent but capped" messages. Overlap with the
    // live tail is harmless — wraps dedup by id on ingest.
    private val pager = BackwardRelayPager("concord.channel.history", liveTailSeconds = 0)

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    override fun id(key: ConcordChannelHistoryQueryState) = ConcordChannelId(key.communityId, key.channelId)

    // This channel's persistent paging cursors, held on its LocalCache ConcordChannel.
    private fun cursorsFor(key: ConcordChannelHistoryQueryState) = LocalCache.getOrCreateConcordChannel(id(key)).history

    /** The community's bootstrap relays — a channel plane may be mirrored on all of them. */
    private fun relaysFor(key: ConcordChannelHistoryQueryState): Set<NormalizedRelayUrl> =
        key.account.concordChannelList.liveCommunities.value
            .firstOrNull { it.id == key.communityId }
            ?.relays
            ?.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
            ?: emptySet()

    /**
     * The channel's derived Chat Plane pubkeys across every epoch (current + each prior epoch we hold
     * a root for) — the REQ authors. A CORD-06 Refounding moves the plane per epoch, so requesting the
     * union lets one backward `until` sweep walk the whole cross-Refounding timeline and reach messages
     * older than the last Refounding. Empty until the Control Plane folds the channel.
     */
    private fun planePksFor(key: ConcordChannelHistoryQueryState): List<String> =
        key.account.concordSessions
            .sessionFor(key.communityId)
            ?.channelPlaneAddressesAllEpochs(key.channelId)
            .orEmpty()

    override fun updateFilter(
        key: ConcordChannelHistoryQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val planePks = planePksFor(key)
        if (planePks.isEmpty()) return emptyList()
        val relays = relaysFor(key)
        // Only armed (advanced, not done) relays carry a REQ, each at its own requested cursor. A parked
        // relay keeps the same filter here, so re-assembly (another relay advancing) doesn't re-REQ it.
        val armed = pager.armedRelays(relays)
        if (armed.isEmpty()) return emptyList()
        return armed.mapNotNull { relay ->
            val until = pager.requestedUntilFor(relay) ?: return@mapNotNull null
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.COMMUNITY_CHATS,
                        purposeDetail = "concord channel history",
                        entityIds = listOf(key.communityId),
                        kinds = listOf(ConcordStreamEnvelope.KIND_WRAP),
                        // All epoch planes at once: the relay serves them interleaved by created_at, so
                        // one backward cursor walks across the Refounding boundaries; "exhausted" then
                        // means every epoch is drained, not just the current one.
                        authors = planePks,
                        until = until,
                        limit = pager.pageLimit,
                    ),
            )
        }
    }

    /** Steps a single [relay] to its next, older page for the open channel. Driven by its on-screen marker. */
    fun advance(relay: NormalizedRelayUrl) {
        if (pager.advance(relay)) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. For a channel too short to scroll. */
    fun advanceAll() {
        if (pager.advanceAll()) invalidateFilters()
    }

    override fun endSub(
        key: ConcordChannelId,
        subId: String,
    ) {
        super.endSub(key, subId)
        // The subscription is gone: let go of what the app-wide pager holds for it.
        pager.unbind(key)
    }

    override fun newSub(key: ConcordChannelHistoryQueryState): Subscription {
        // Repoint the single-active orchestrator at this channel's cursors and its community relays.
        pager.bind(cursorsFor(key), key.account.scope, owner = id(key)) { relaysFor(key) }
        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: ConcordChannelHistoryQueryState): SubscriptionListener {
        // A just-backgrounded channel's subscription can still deliver after the orchestrator rebinds to
        // another channel; gate the pager (single-active) on whether it's still bound to THIS channel's
        // cursors so a late callback can't move another channel's cursors. newEose runs regardless.
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
