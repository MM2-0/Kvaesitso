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
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol

/**
 * The lobby's account-scoped part: the user's own games, the moves of the games on screen
 * ([watchedGames], the start ids the lobby lists), and the challenges of the user's games that
 * never arrived ([missingStarts]).
 */
@Stable
class ChessLobbyGamesQueryState(
    override val account: Account,
    val watchedGames: Set<HexKey>,
    val missingStarts: Set<HexKey> = emptySet(),
) : AccountScopedQuery

/**
 * What the chess screen needs besides the top-bar list ([ChessFilterAssembler]):
 *
 * - the user's games, whatever the top bar has picked: what they started or moved in (their
 *   outbox), and what challenges them (their inbox), by `p` tag and by the private reference
 *   jesterui files direct challenges under, plus the challenges of their games that never arrived;
 * - the moves of the listed games, from the relays each game was seen on and the user's own, so
 *   a card can say whose turn it is. The REQ stays open while the lobby is, so moves stream in.
 */
@Stable
class ChessLobbyGamesFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<ChessLobbyGamesQueryState>() {
    val group = listOf(ChessLobbyGamesSubAssembler(cache, client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class ChessLobbyGamesSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<ChessLobbyGamesQueryState>,
) : PerUniqueIdEoseManager<ChessLobbyGamesQueryState, HexKey>(client, allKeys) {
    override fun updateFilter(
        key: ChessLobbyGamesQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val me = key.account.userProfile().pubkeyHex
        val outbox = key.account.outboxRelays.flow.value
        val inbox = key.account.notificationRelays.flow.value

        val authored =
            outbox.map {
                RelayBasedFilter(
                    relay = it,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.GAMES,
                            purposeDetail = "My chess games",
                            authors = listOf(me),
                            kinds = listOf(JesterEvent.KIND),
                            limit = 500,
                            since = since?.get(it)?.time,
                        ),
                )
            }

        val challenged =
            inbox.map {
                RelayBasedFilter(
                    relay = it,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.GAMES,
                            purposeDetail = "Chess games with me",
                            kinds = listOf(JesterEvent.KIND),
                            tags = mapOf("p" to listOf(me)),
                            limit = 500,
                            since = since?.get(it)?.time,
                        ),
                )
            } +
                inbox.map {
                    RelayBasedFilter(
                        relay = it,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.GAMES,
                                purposeDetail = "Chess challenges to me",
                                kinds = listOf(JesterEvent.KIND),
                                tags = mapOf("e" to listOf(JesterProtocol.privateStartRef(me))),
                                limit = 100,
                                since = since?.get(it)?.time,
                            ),
                    )
                }

        return authored + challenged + watchedMoves(key, outbox + inbox) + missingStarts(key, outbox + inbox)
    }

    // A game's challenge is wherever its moves were seen, or on the user's own relays.
    private fun missingStarts(
        key: ChessLobbyGamesQueryState,
        myRelays: Set<NormalizedRelayUrl>,
    ): List<RelayBasedFilter> {
        if (key.missingStarts.isEmpty()) return emptyList()

        val startsPerRelay = mutableMapOf<NormalizedRelayUrl, MutableSet<HexKey>>()
        key.missingStarts.forEach { startId ->
            val seenOn =
                cache
                    .getNoteIfExists(startId)
                    ?.replies
                    ?.flatMapTo(HashSet()) { it.relays }
                    .orEmpty()
            (seenOn + myRelays).forEach { relay -> startsPerRelay.getOrPut(relay) { mutableSetOf() }.add(startId) }
        }

        return startsPerRelay.map { (relay, ids) ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.GAMES,
                        purposeDetail = "Challenges of my chess games",
                        ids = ids.sorted(),
                        kinds = listOf(JesterEvent.KIND),
                    ),
            )
        }
    }

    // No `since`: a game that just came on screen needs its moves from before the last EOSE too.
    private fun watchedMoves(
        key: ChessLobbyGamesQueryState,
        myRelays: Set<NormalizedRelayUrl>,
    ): List<RelayBasedFilter> {
        if (key.watchedGames.isEmpty()) return emptyList()

        val gamesPerRelay = mutableMapOf<NormalizedRelayUrl, MutableSet<HexKey>>()
        key.watchedGames.forEach { gameId ->
            val seenOn = cache.getNoteIfExists(gameId)?.relays.orEmpty()
            (seenOn + myRelays).forEach { relay -> gamesPerRelay.getOrPut(relay) { mutableSetOf() }.add(gameId) }
        }

        return gamesPerRelay.map { (relay, games) ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.GAMES,
                        purposeDetail = "Chess lobby moves",
                        kinds = listOf(JesterEvent.KIND),
                        tags = mapOf("e" to games.sorted()),
                        limit = 1000,
                    ),
            )
        }
    }

    override fun id(key: ChessLobbyGamesQueryState) = key.account.userProfile().pubkeyHex
}
