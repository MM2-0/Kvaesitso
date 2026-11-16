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
package com.vitorpamplona.amethyst.commons.relayClient.wallet

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent

/**
 * Per-screen subscription key for the on-chain wallet history.
 *
 * @property user the logged-in user whose taproot address is being viewed.
 * @property windowSinceSeconds lower bound (`since`) for the relay query, in
 *           seconds. Derived from the oldest visible chain transaction's
 *           `blockTime` so we only fetch zap events relevant to what the
 *           screen will actually display. `null` disables the lower bound —
 *           the relay returns the full history capped by `limit`.
 */
data class OnchainZapsQueryState(
    val user: User,
    val windowSinceSeconds: Long?,
)

private const val PER_FILTER_LIMIT = 500

/**
 * Build per-relay filters for kind-8333 zaps that involve [pubkey] — either
 * as the recipient (`p`-tag, incoming) or the author (outgoing). The
 * effective `since` is the larger of the visible-window lower bound (so we
 * don't drag the whole history every time) and any EOSE-tracked timestamp
 * from a previous round (so we only ask the relay for events newer than
 * what we already have).
 */
private fun filterOnchainZaps(
    pubkey: String,
    relays: Collection<NormalizedRelayUrl>,
    windowSinceSeconds: Long?,
    eoseSince: SincePerRelayMap?,
): List<RelayBasedFilter> =
    relays.flatMap { relay ->
        val eoseTime = eoseSince?.get(relay)?.time
        val since =
            listOfNotNull(windowSinceSeconds, eoseTime)
                .maxOrNull()
                ?.takeIf { it > 0L }

        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.WALLET,
                        kinds = listOf(OnchainZapEvent.KIND),
                        tags = mapOf("p" to listOf(pubkey)),
                        limit = PER_FILTER_LIMIT,
                        since = since,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.WALLET,
                        kinds = listOf(OnchainZapEvent.KIND),
                        authors = listOf(pubkey),
                        limit = PER_FILTER_LIMIT,
                        since = since,
                    ),
            ),
        )
    }

class OnchainZapsFilterSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<OnchainZapsQueryState>,
) : SingleSubEoseManager<OnchainZapsQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<OnchainZapsQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val key = keys.firstOrNull() ?: return emptyList()
        val user = key.user
        val relays: Collection<NormalizedRelayUrl> =
            user.inboxRelays()?.ifEmpty { null }
                ?: user.outboxRelays()?.ifEmpty { null }
                ?: user.allUsedRelaysOrNull()
                ?: cache.relayHints.hintsForKey(user.pubkeyHex)
        if (relays.isEmpty()) return emptyList()

        // Take the tightest window across all live keys — if any subscriber
        // is showing older transactions, widen the query to cover them too.
        val windowSince =
            keys
                .mapNotNull { it.windowSinceSeconds }
                .minOrNull()
                ?.takeIf { keys.all { k -> k.windowSinceSeconds != null } }

        return filterOnchainZaps(user.pubkeyHex, relays, windowSince, since)
    }

    override fun distinct(key: OnchainZapsQueryState) = key.user.pubkeyHex
}

class OnchainZapsFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<OnchainZapsQueryState>() {
    private val sub = OnchainZapsFilterSubAssembler(cache, client, ::allKeys)

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = sub.destroy()
}
