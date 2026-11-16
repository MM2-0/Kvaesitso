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
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent

/**
 * Per-room state for the lightweight liveness probe — used by feed
 * thumbnail cards to flip the LIVE / ENDED badge based on whether
 * any speaker has published a fresh kind-10312 presence.
 *
 * Distinct from [NestRoomQueryState] (which fans out to chat,
 * presence, reactions, and admin commands for an actively-joined
 * room) — the feed should not pay a popular room's full chat
 * history just to render a status pill.
 */
@Stable
class NestRoomLivenessQueryState(
    val note: AddressableNote,
    override val account: Account,
) : AccountScopedQuery

/**
 * Single per-room sub-assembler for the feed thumbnail liveness
 * probe. Issues ONE narrow [Filter] per outbox relay:
 *
 *   `kinds=[10312]`, `#a=[roomATag]`, `limit=1`
 *
 * Net wire change: the previous code reused [NestRoomFilterSubAssembler]
 * for the badge probe, which subscribes to chat + presence + reactions
 * (kinds 1311, 10312, 7) — so a feed of N rooms cost N popular-room
 * chat-history pulls per relay. This probe asks for the single
 * latest presence per room and nothing else.
 */
class NestRoomLivenessSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NestRoomLivenessQueryState>,
) : PerUniqueIdEoseManager<NestRoomLivenessQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: NestRoomLivenessQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays =
            key.account.outboxRelays.flow.value +
                key.note.relays +
                ((key.note.event as? MeetingSpaceEvent)?.relays()?.toSet() ?: emptySet())

        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.LIVE_ROOMS,
                        kinds = listOf(MeetingRoomPresenceEvent.KIND),
                        tags = mapOf("a" to listOf(key.note.idHex)),
                        // limit=1: the badge only needs the freshest
                        // heartbeat — older ones don't change the
                        // verdict. since is still threaded so the
                        // EOSE manager can resume after a reconnect.
                        limit = 1,
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: NestRoomLivenessQueryState): String = key.note.idHex
}

@Stable
class NestRoomLivenessAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NestRoomLivenessQueryState>() {
    private val sub = NestRoomLivenessSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}
