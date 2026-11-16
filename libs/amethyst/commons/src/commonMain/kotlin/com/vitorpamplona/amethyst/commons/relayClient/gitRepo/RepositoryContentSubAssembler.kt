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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.mapOfSet

class RepositoryContentSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<RepositoryQueryState>,
) : SingleSubEoseManager<RepositoryQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<RepositoryQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        val relaysPerRepo =
            keys.mapNotNull { key ->
                val repoEvent = key.repository.event as? GitRepositoryEvent ?: return@mapNotNull null

                Triple(key, repoEvent, repositoryRelays(cache, key.repository, repoEvent))
            }

        // One cursor per relay is shared by every repository opened on it.
        val sinceNow =
            sinceDroppingGrownRelays(
                mapOfSet { relaysPerRepo.forEach { (key, _, relays) -> relays.forEach { add(it, key.repository.idHex) } } },
                since,
            )

        return relaysPerRepo.flatMap { (_, repoEvent, repoRelays) ->
            repoRelays.flatMap { relay ->
                val relaySince = sinceNow?.get(relay)?.time
                filterRepositoryContent(relay, repoEvent, relaySince) + filterRepositoryCi(relay, repoEvent, relaySince)
            }
        }
    }

    override fun distinct(key: RepositoryQueryState) = key.repository.idHex
}

/**
 * Where a repository's items and their activity are read: the relays its announcement lists, else
 * the relays it was seen on and hinted at.
 */
fun repositoryRelays(
    cache: ICacheProvider,
    repository: AddressableNote,
    event: GitRepositoryEvent,
): Set<NormalizedRelayUrl> {
    val announcedRelays = event.relays().mapNotNull(RelayUrlNormalizer::normalizeOrNull)
    val hintRelays =
        if (announcedRelays.isEmpty()) {
            cache.relayHints.hintsForAddress(event.addressTag())
        } else {
            emptyList()
        }
    return (announcedRelays + hintRelays + repository.relayUrls()).toSet()
}
