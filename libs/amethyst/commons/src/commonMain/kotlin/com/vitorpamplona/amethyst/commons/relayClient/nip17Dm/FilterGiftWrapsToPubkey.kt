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
package com.vitorpamplona.amethyst.commons.relayClient.nip17Dm

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

fun filterGiftWrapsToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
    until: Long? = null,
    limit: Int? = null,
    kinds: List<Int> = listOf(GiftWrapEvent.KIND, EphemeralGiftWrapEvent.KIND),
    purpose: SubPurpose = SubPurpose.DIRECT_MESSAGES,
    purposeDetail: String? = null,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = purpose,
                    purposeDetail = purposeDetail,
                    kinds = kinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    // A gift wrap's outer created_at is randomized up to 2 days before the real
                    // message time, so widen the lower bound by 2 days to catch wraps for messages
                    // right at the floor. (The upper bound needs no margin: a slice's `until` is the
                    // previous slice's un-margined floor, so the 2-day overlap already covers the seam.)
                    since = since?.minus(TimeUtils.twoDays()),
                    until = until,
                    limit = limit,
                ),
        ),
    )
}
