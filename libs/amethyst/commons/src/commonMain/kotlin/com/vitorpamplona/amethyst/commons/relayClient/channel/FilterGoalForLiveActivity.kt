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
package com.vitorpamplona.amethyst.commons.relayClient.channel

import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent

/**
 * Fetches the NIP-75 zap goal referenced by a live stream plus the zap receipts
 * that count toward it.
 *
 * zap.stream attaches a goal to a 30311 event via `["goal", "<hex event id>"]`.
 * We fetch the 9041 goal event by id and the 9735 zap receipts that `#e`-tag it.
 */
fun filterGoalForLiveActivities(
    channel: LiveActivitiesChannel,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val goalId = channel.info?.goalEventId() ?: return emptyList()

    return channel.relays().toSet().flatMap { relay ->
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.LIVE_CHAT,
                        kinds = listOf(ZapGoalEvent.KIND),
                        ids = listOf(goalId),
                        limit = 1,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.LIVE_CHAT,
                        kinds = listOf(ZapReceiptEvent.KIND, OnchainZapEvent.KIND, Bolt12ZapEvent.KIND),
                        tags = mapOf("e" to listOf(goalId)),
                        limit = 200,
                        since = since?.get(relay)?.time,
                    ),
            ),
        )
    }
}
