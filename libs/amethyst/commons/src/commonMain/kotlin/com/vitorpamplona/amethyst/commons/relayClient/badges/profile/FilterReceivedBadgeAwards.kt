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
package com.vitorpamplona.amethyst.commons.relayClient.badges.profile

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent

/**
 * Pulls every badge award (kind 8) tagging this pubkey, from the user's
 * notification relays. Used by the "Profile badges" management screen to
 * back-fill the full history of awards (the always-on notifications
 * subscription is bounded by `since`, so older awards may not be in
 * cache yet).
 */
fun filterReceivedBadgeAwards(
    pubkey: HexKey,
    relays: Set<NormalizedRelayUrl>,
): List<RelayBasedFilter> {
    if (pubkey.isEmpty() || relays.isEmpty()) return emptyList()
    val pTags = listOf(pubkey)
    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(BadgeAwardEvent.KIND),
                    tags = mapOf("p" to pTags),
                    limit = 500,
                ),
        )
    }
}
