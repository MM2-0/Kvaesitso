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
package com.vitorpamplona.amethyst.commons.relayClient.search

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex

/**
 * Creates a new sub for each Search Screen. The StateFlow of the text field
 * that has the search ter is the id of the sub.
 */
class SearchUserWatcherSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<SearchQueryState>,
) : PerUniqueIdEoseManager<SearchQueryState, Int>(client, allKeys) {
    override fun updateFilter(
        key: SearchQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val mySearchString = key.searchQuery.value

        if (mySearchString.isBlank()) return null

        val indexRelays =
            key.indexerRelays.value
                .ifEmpty { DefaultIndexerRelayList }
        val defaultRelaysWithSearch = key.followPlusAllMineWithSearchRelays.value

        val directFilters =
            runCatching {
                if (Hex.isHex(mySearchString)) {
                    val hexKey = Hex.decode(mySearchString).toHexKey()
                    filterByAuthor(cache, hexKey, indexRelays, defaultRelaysWithSearch)
                } else {
                    val parsed = Nip19Parser.uriToRoute(mySearchString)?.entity
                    if (parsed != null) {
                        cache.consume(parsed)

                        when (parsed) {
                            is NSec -> filterByAuthor(cache, parsed.toPubKeyHex(), indexRelays, defaultRelaysWithSearch)
                            is NPub -> filterByAuthor(cache, parsed.hex, indexRelays, defaultRelaysWithSearch)
                            is NProfile -> filterByAuthor(cache, parsed.hex, indexRelays, defaultRelaysWithSearch)
                            is NNote -> emptyList()
                            is NEvent -> emptyList()
                            is NEmbed -> emptyList()
                            is NRelay -> emptyList()
                            is NAddress -> emptyList()
                            else -> emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            }.getOrDefault(emptyList())

        val searchFilters =
            key.searchRelays.value.flatMap {
                searchPeopleByName(mySearchString, it)
            }

        val filters = directFilters + searchFilters
        key.startedAsking(mySearchString, filters.mapTo(mutableSetOf()) { it.relay })
        return filters
    }

    // See the posts assembler: the screen needs to know who has answered, not just when.
    override fun newEose(
        key: SearchQueryState,
        relayUrl: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        super.newEose(key, relayUrl, time, filters)
        key.answeredBy(relayUrl)
    }

    override fun id(key: SearchQueryState) = key.searchQuery.hashCode()
}
