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
package com.vitorpamplona.amethyst.commons.nests.datasource

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent

/**
 * Per-room state for every wire subscription scoped to a single
 * Nest. Combines what used to be four parallel `*QueryState`s
 * (chat, presence, reactions, admin commands) into one — the four
 * relay REQs collapsed into a single subscription with two
 * [Filter]s per relay (see [NestRoomFilterSubAssembler.updateFilter]).
 */
@Stable
class NestRoomQueryState(
    val note: AddressableNote,
    override val account: Account,
) : AccountScopedQuery

/**
 * Single per-room sub-assembler. Issues two [Filter]s per outbox
 * relay on every key update:
 *
 *   1. `kinds=[1311, 10312, 7, 9735]`, `#a=[roomATag]` — the chat,
 *      presence, reaction and zap-receipt streams the room consumes.
 *      Bundling them into one Filter is safe: a single Filter
 *      requires (kind ∈ kinds) AND (every tag dimension matches),
 *      and these streams all share the same `#a` gate.
 *
 *   2. `kinds=[4312]`, `#a=[roomATag]`, `#p=[localPubkey]` — admin
 *      commands. Stays separate from (1) because the additional
 *      `#p` constraint must NOT apply to the chat/presence/reaction
 *      streams (they don't tag the local user). A second Filter on
 *      the same REQ keeps them as one wire subscription.
 *
 * Replaces the previous fan-out of four sibling assemblers — chat,
 * presence, reactions, admin commands — that each opened their own
 * REQ. Net wire change: 4 REQs per relay → 1 REQ per relay with 2
 * Filters, same event coverage.
 */
class NestRoomFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NestRoomQueryState>,
) : PerUniqueIdEoseManager<NestRoomQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: NestRoomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays = key.account.outboxRelays.flow.value + key.note.relays + ((key.note.event as? MeetingSpaceEvent)?.relays()?.toSet() ?: emptySet())

        return relays.flatMap { relay ->
            listOf(
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.LIVE_ROOMS,
                            kinds =
                                listOf(
                                    LiveActivitiesChatMessageEvent.KIND,
                                    MeetingRoomPresenceEvent.KIND,
                                    ReactionEvent.KIND,
                                    ZapReceiptEvent.KIND,
                                    Bolt12ZapEvent.KIND,
                                ),
                            tags = mapOf("a" to listOf(key.note.idHex)),
                            since = since?.get(relay)?.time,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.LIVE_ROOMS,
                            kinds = listOf(AdminCommandEvent.KIND),
                            tags = mapOf("a" to listOf(key.note.idHex), "p" to listOf(key.account.pubKey)),
                            since = since?.get(relay)?.time,
                        ),
                ),
                // Live-update subscription for the room event itself.
                // EventFinder loads it once when missing and stops; the
                // discovery-side feeds (FilterLiveActivitiesBy*) don't
                // run while the user is inside a room. So without this
                // third filter, a kind-30312 edit on the host's end
                // (remove-from-stage, role change, status flip) never
                // reaches the in-room screen — the demoted speaker
                // keeps seeing themselves on stage and broadcasting,
                // because participantGrid is built from a frozen event.
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.LIVE_ROOMS,
                            kinds = listOf(MeetingSpaceEvent.KIND),
                            authors = listOf(key.note.address.pubKeyHex),
                            tags = mapOf("d" to listOf(key.note.address.dTag)),
                            since = since?.get(relay)?.time,
                        ),
                ),
            )
        }
    }

    override fun id(key: NestRoomQueryState): String = key.note.idHex
}

@Stable
class NestRoomFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NestRoomQueryState>() {
    private val sub = NestRoomFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}
