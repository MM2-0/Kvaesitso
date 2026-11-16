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
package com.vitorpamplona.amethyst.commons.relayClient.drss

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent

/** A drss.io show's bot, whose latest episodes (kind 30023) a show card lists. */
class DrssShowEpisodesQueryState(
    val bot: User,
)

/** How many of a show's latest episodes a card asks for. */
const val DRSS_SHOW_EPISODES_LIMIT = 5

/** Loads the latest episodes of the shows on screen, one subscription per show bot. */
class DrssShowEpisodesFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<DrssShowEpisodesQueryState>() {
    val group = listOf(DrssShowEpisodesSubAssembler(cache, client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class DrssShowEpisodesSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<DrssShowEpisodesQueryState>,
) : PerUserEoseManager<DrssShowEpisodesQueryState>(client, allKeys) {
    override fun updateFilter(
        key: DrssShowEpisodesQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = filterDrssShowEpisodes(cache, key.bot, since)

    override fun user(key: DrssShowEpisodesQueryState) = key.bot
}

/** The bot's latest articles, from its outbox, or wherever it has been seen when it lists none. */
fun filterDrssShowEpisodes(
    cache: ICacheProvider,
    bot: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        bot.outboxRelays()?.ifEmpty { null }
            ?: (bot.allUsedRelays() + cache.relayHints.hintsForKey(bot.pubkeyHex))

    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.MEDIA_FEED,
                    kinds = listOf(LongFormContentEvent.KIND),
                    authors = listOf(bot.pubkeyHex),
                    limit = DRSS_SHOW_EPISODES_LIMIT,
                    since = since?.get(relay)?.time,
                ),
        )
    }
}
