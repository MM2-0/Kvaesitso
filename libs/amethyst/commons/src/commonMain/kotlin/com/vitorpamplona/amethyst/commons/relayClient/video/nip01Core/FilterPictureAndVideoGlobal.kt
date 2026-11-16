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
package com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.video.LegacyMimeTypeMap
import com.vitorpamplona.amethyst.commons.relayClient.video.PictureAndVideoKinds
import com.vitorpamplona.amethyst.commons.relayClient.video.PictureAndVideoLegacyKinds
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun filterPictureAndVideoGlobal(
    relays: GlobalTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (relays.set.isEmpty()) return emptyList()

    return relays.set.flatMap {
        val since = since?.get(it.key)?.time ?: defaultSince
        listOf(
            RelayBasedFilter(
                relay = it.key,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.MEDIA_FEED,
                        kinds = PictureAndVideoKinds,
                        limit = 50,
                        since = since,
                    ),
            ),
            RelayBasedFilter(
                relay = it.key,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.MEDIA_FEED,
                        kinds = PictureAndVideoLegacyKinds,
                        tags = LegacyMimeTypeMap,
                        limit = 50,
                        since = since,
                    ),
            ),
        )
    }
}
