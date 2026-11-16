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

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent

/**
 * The same app filter [filterSoftwareAppsGlobal] sends each relay, sent only to the one relay
 * picked in the top bar. A single picked relay means only that relay, so zapstore's relay is NOT
 * added here, and a picked relay the user blocked gets nothing. It starts from that relay's own
 * cursor or [defaultSince] with no recent-window fallback: a relay picked on its own may have
 * nothing in a recent window, so a quiet relay's feed would come up empty. The REQ's limit bounds
 * it instead.
 */
fun filterSoftwareAppsByRelay(
    relayFilter: RelayTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
    blockedRelays: Set<NormalizedRelayUrl> = emptySet(),
): List<RelayBasedFilter> {
    val relayUrl = relayFilter.relayUrl
    if (relayUrl in blockedRelays) return emptyList()

    val since = since?.get(relayUrl)?.time ?: defaultSince

    return listOf(
        RelayBasedFilter(
            relay = relayUrl,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(SoftwareApplicationEvent.KIND, ReleaseArtifactSetEvent.KIND),
                    // Keep the limit < 100. Relays such as zapstore's score filter
                    // specificity and treat a limit >= 100 (or none) as too vague,
                    // rejecting the whole REQ with "filters are too vague".
                    limit = 99,
                    since = since,
                ),
        ),
    )
}
