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
package com.vitorpamplona.amethyst.commons.relayClient.apps.recommendations

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent

/**
 * Pulls this user's own kind 31989 recommendation events from their outbox
 * relays so the "Recommended apps" management screen starts from the current
 * published state even when the profile screen hasn't been visited yet.
 */
fun filterMyAppRecommendations(
    pubkey: HexKey,
    relays: Set<NormalizedRelayUrl>,
): List<RelayBasedFilter> {
    if (pubkey.isEmpty() || relays.isEmpty()) return emptyList()
    val authors = listOf(pubkey)
    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(AppRecommendationEvent.KIND),
                    authors = authors,
                    limit = 100,
                ),
        )
    }
}

/**
 * Pulls recent kind 31990 app definitions so the management screen has
 * candidates to recommend beyond what already happens to sit in cache.
 */
fun filterRecentAppDefinitions(relays: Set<NormalizedRelayUrl>): List<RelayBasedFilter> {
    if (relays.isEmpty()) return emptyList()
    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(AppDefinitionEvent.KIND),
                    limit = 100,
                ),
        )
    }
}
