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

import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedSubAssembler
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import kotlinx.coroutines.flow.Flow

class SoftwareAppsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<SoftwareAppsQueryState>,
) : TopNavFeedSubAssembler<SoftwareAppsQueryState>(client, allKeys) {
    override fun updateFilter(
        key: SoftwareAppsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> =
        makeSoftwareAppsFilter(
            key.followsPerRelay.value,
            since,
            key.feeds.first().lastNoteCreatedAtIfFilled(),
            key.blockedRelays.value,
        )

    // Re-evaluate when the user blocks/unblocks zapstore's relay so the global Apps feed
    // stops/starts querying it without a restart.
    override fun extraInvalidators(key: SoftwareAppsQueryState): List<Flow<*>> = listOf(key.blockedRelays)
}
