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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterGroupNotificationsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupAuxFilter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupJoinedChatTailFilter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupLiveStateFilter
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/** One joined group whose recent chat is kept live for the Messages-list preview. */
class RelayGroupJoinedChatTailQueryState(
    override val account: Account,
    val groupId: GroupId,
) : AccountScopedQuery

/**
 * Always-on **live tail** for the recent chat of every NIP-29 group the user has joined — the group
 * analog of the NIP-04 rooms-list live tail
 * ([com.vitorpamplona.amethyst.commons.chats.rooms.datasource.ChatroomListNip04SubAssembler]).
 *
 * **One subscription per joined group**, each a single-valued `#h` filter with `since = recentBoundary()`
 * and no `limit` — a time floor bounds it, so it stays reconnect-safe. This keeps the Messages-list
 * previews reflecting the true newest message, and joined groups' recent chat warm in cache, without
 * opening each one. Older history is the [RelayGroupOpenChatHistorySubAssembler]'s job (below the floor).
 *
 * These used to be batched into one subscription per host relay carrying every group id in a single `#h`.
 * That is valid NIP-01 and relays answer it correctly for stored queries, but it silently breaks *live*
 * delivery on `block/buzz`, which can only index a subscription under one channel and downgrades a
 * multi-id one to "global" — a class that by design receives no channel-scoped events. The batched tail
 * therefore backfilled at EOSE and then went permanently deaf, freezing the Messages row while the open
 * chat (always single-valued) stayed live. See [buildRelayGroupJoinedChatTailFilter] for the full trace.
 */
class RelayGroupJoinedChatTailFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupJoinedChatTailQueryState>() {
    val tail = RelayGroupJoinedChatTailSubAssembler(client, ::allKeys)

    val group = listOf(tail)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupJoinedChatTailSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupJoinedChatTailQueryState>,
) : PerUniqueIdEoseManager<RelayGroupJoinedChatTailQueryState, GroupId>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("relayGroup.preview.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: RelayGroupJoinedChatTailQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        // The preload only mounts keys while the toggle is on; re-checked here so a flip to off
        // empties any subscription that outlives the recomposition.
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP29)) return null

        val relay = key.groupId.relayUrl
        // The tracker is shared by every key of this assembler, so it must describe the whole joined
        // fleet rather than whichever channel happened to rebuild last — otherwise each key would
        // overwrite the expected set with its own single relay. [loadingMore] is what the Messages rows
        // read to tell "still fetching this channel's newest message" apart from "channel is empty".
        windowLoad.setExpectedRelays(
            key.account.relayGroupList.liveRelayGroupList.value
                .mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it.relayUrl) },
        )

        // Two filters, both `#h`-scoped to THIS group, so the subscription still resolves to a single
        // channel on buzz (its resolver only bails when two *distinct* ids appear, or when some filter
        // carries no channel tag at all). Each is queried independently, so they keep their own window:
        //  - the chat tail: every timeline kind, time-floored, no limit
        //  - group activity addressed to me: reactions/zaps/reports/replies (kinds the tail doesn't ask
        //    for), `#p` = me + `limit`, `since` from EOSE so a cold start is all-time and a reconnect is
        //    cheap. This used to ride the account-wide notifications subscription, which also carries
        //    inbox filters with no `#h` — that alone forces the whole subscription global on buzz, so
        //    live group reactions never arrived until the next launch.
        return listOf(
            buildRelayGroupJoinedChatTailFilter(key.groupId, DmHistoryTuning.recentBoundary()),
            // The row's newest message at ANY age. The tail above floors at 7 days to keep recent chat
            // warm, which on its own strands a channel quiet for longer than that on a placeholder row.
            buildRelayGroupPreviewFilter(key.groupId, since?.get(relay)?.time),
            // Reactions/deletions for every message in this channel — the shape Buzz's own client uses.
            buildRelayGroupAuxFilter(key.groupId, DmHistoryTuning.recentBoundary()),
        ) +
            // This group's own state (39000-39003 + pins), `#h`-scoped so Buzz streams it. The
            // account-wide state subscription asks by `#d`, which carries no channel tag and so
            // registers as a global subscription there — and Buzz never fans a channel-scoped event
            // to one of those, which is why a rename or a role change used to sit stale until the
            // next cold start. Costs nothing on a relay29 relay, where it simply matches nothing.
            (if (BuzzRelayDialect.isBuzz(relay)) buildRelayGroupLiveStateFilter(key.groupId) else emptyList()) +
            filterGroupNotificationsToPubkey(
                relay = relay,
                pubkey = key.account.userProfile().pubkeyHex,
                groupIds = listOf(key.groupId.id),
                since = since?.get(relay)?.time,
            )
    }

    override fun id(key: RelayGroupJoinedChatTailQueryState) = key.groupId

    override fun newSub(key: RelayGroupJoinedChatTailQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay: NormalizedRelayUrl, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
