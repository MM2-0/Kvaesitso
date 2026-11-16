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
package com.vitorpamplona.amethyst.commons.relayClient.softwareapps

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

// Each open app page holds its own state, even when two screens show the same app.
class SoftwareReleasesQueryState(
    val app: AddressableNote,
)

/**
 * Loads the NIP-82 releases (kind 30063) of the app whose page is open. The app page reads its
 * releases from the cache; without this, an app opened from a link, a profile or a mention only
 * showed releases some feed had happened to fetch — usually none.
 */
class SoftwareReleasesFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<SoftwareReleasesQueryState>() {
    val group = listOf(SoftwareReleasesSubAssembler(cache, client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

/**
 * Asks for the app's releases where they are likely to be: the relays the app itself came from,
 * the hints recorded for its address, and its publisher's outbox. The filter is the one the page
 * reads the cache with ([SoftwareReleases.filter]): only the publisher and the maintainers it
 * credits, by the app's `i` id.
 *
 * One subscription and one EOSE cursor per app: a cursor shared by every app would hand the second
 * app opened the first one's `since`, and its older releases would never be asked for.
 */
class SoftwareReleasesSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<SoftwareReleasesQueryState>,
) : PerUniqueIdEoseManager<SoftwareReleasesQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: SoftwareReleasesQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val app = key.app.event as? SoftwareApplicationEvent ?: return emptyList()
        val wanted = SoftwareReleases.filter(app)
        val relays =
            (
                key.app.relayUrls() +
                    cache.relayHints.hintsForAddress(key.app.idHex) +
                    cache.getUserIfExists(app.pubKey)?.outboxRelays().orEmpty()
            ).toSet()

        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.ADD_ONS,
                        kinds = wanted.kinds,
                        authors = wanted.authors,
                        tags = wanted.tags,
                        limit = 100,
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    // Keyed on the app version too: a newer app event can credit a new maintainer, whose releases
    // are older than the cursor the previous version earned.
    override fun id(key: SoftwareReleasesQueryState) = key.app.idHex + ":" + key.app.event?.id
}
