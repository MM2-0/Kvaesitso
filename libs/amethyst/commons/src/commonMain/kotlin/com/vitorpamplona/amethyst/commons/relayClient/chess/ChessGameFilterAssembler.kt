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
package com.vitorpamplona.amethyst.commons.relayClient.chess

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent

/** One open chess board. Two boards on the same game share the subscription underneath. */
@Stable
class ChessGameQueryState(
    val gameId: HexKey,
    override val account: Account,
) : AccountScopedQuery

/**
 * Streams one game while its board is on screen: the start event and every move that points at
 * it. The REQ stays open, so the relays the game lives on stay connected and each move arrives as
 * it is published; there is no polling.
 *
 * The relays are where the game was seen, where its players write and read, and the user's own.
 * As moves arrive from new players or relays, the filter re-assembles to include them.
 */
@Stable
class ChessGameFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<ChessGameQueryState>() {
    val group = listOf(ChessGameSubAssembler(cache, client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class ChessGameSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<ChessGameQueryState>,
) : PerUniqueIdEoseManager<ChessGameQueryState, HexKey>(client, allKeys) {
    override fun updateFilter(
        key: ChessGameQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> =
        chessGameRelays(key).map {
            RelayBasedFilter(
                relay = it,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.GAMES,
                        purposeDetail = "Chess game",
                        kinds = listOf(JesterEvent.KIND),
                        tags = mapOf("e" to listOf(key.gameId)),
                        since = since?.get(it)?.time,
                    ),
            )
        } +
            // The start event refers to the start position, not to itself, so it needs its own
            // REQ. It never changes: once it is here, it is not asked for again.
            if (cache.getNoteIfExists(key.gameId)?.event == null) {
                chessGameRelays(key).map {
                    RelayBasedFilter(
                        relay = it,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.GAMES,
                                purposeDetail = "Chess game start",
                                ids = listOf(key.gameId),
                            ),
                    )
                }
            } else {
                emptyList()
            }

    private fun chessGameRelays(key: ChessGameQueryState): Set<NormalizedRelayUrl> {
        val account = key.account
        val relays = mutableSetOf<NormalizedRelayUrl>()
        relays.addAll(account.outboxRelays.flow.value)
        relays.addAll(account.notificationRelays.flow.value)
        relays.addAll(cache.relayHints.hintsForEvent(key.gameId))

        val start = cache.getNoteIfExists(key.gameId)
        if (start != null) {
            relays.addAll(start.relays)
            val players = mutableSetOf<HexKey>()
            (start.event as? JesterEvent)?.let {
                players.add(it.pubKey)
                players.addAll(it.opponentKeys())
            }
            start.replies.forEach { move ->
                relays.addAll(move.relays)
                move.author?.pubkeyHex?.let { players.add(it) }
            }
            players.remove(account.userProfile().pubkeyHex)
            players.forEach { player ->
                val user = cache.getUserIfExists(player) ?: return@forEach
                relays.addAll(user.outboxRelays() ?: user.allUsedRelaysOrNull() ?: cache.relayHints.hintsForKey(player))
            }
        }
        return relays
    }

    override fun id(key: ChessGameQueryState) = key.gameId
}
