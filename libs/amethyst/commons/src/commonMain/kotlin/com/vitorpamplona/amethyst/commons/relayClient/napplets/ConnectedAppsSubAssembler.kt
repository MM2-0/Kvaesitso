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
package com.vitorpamplona.amethyst.commons.relayClient.napplets

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/**
 * Builds relay REQs for napplet manifests limited to the authors stored in
 * [ConnectedAppsQueryState.authors], queried against the account's home relays.
 * The filter is purposely narrow — we only want manifests for apps the user has already
 * connected to, not the full follow-list.
 */
class ConnectedAppsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ConnectedAppsQueryState>,
) : PerUserEoseManager<ConnectedAppsQueryState>(client, allKeys) {
    override fun user(key: ConnectedAppsQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: ConnectedAppsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (key.authors.isEmpty()) return emptyList()
        val relays = key.homeRelays.value
        if (relays.isEmpty()) return emptyList()
        return relays.flatMap { relay ->
            filterNappletsByAuthors(relay, key.authors, since?.get(relay)?.time)
        }
    }
}
