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
package com.vitorpamplona.amethyst.commons.relayClient.thread

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Everything that cites the thread [root] (`e`/`E`, `a`/`A`), asked of the relays where
 * reactions to it land: its author's inbox, where it was seen, declared log relays. When
 * none of those is known, [defaultRelays] are asked instead — the same fallback
 * [filterMissingEventsForThread] applies to events it cannot place.
 */
fun filterEventsInThreadForRoot(
    root: Note,
    since: SincePerRelayMap?,
    defaultRelays: Set<NormalizedRelayUrl> = emptySet(),
): List<RelayBasedFilter> {
    val addressRoot = if (root is AddressableNote) root.idHex else null
    val eventRoot = if (root !is AddressableNote) root.idHex else root.event?.id

    return root.relayUrlsForReactions().toSet().ifEmpty { defaultRelays }.flatMap {
        val since = since?.get(it)?.time

        val addressList =
            if (addressRoot != null) {
                listOf(
                    RelayBasedFilter(
                        relay = it,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.THREAD,
                                tags = mapOf("a" to listOf(addressRoot)),
                                since = since,
                            ),
                    ),
                    RelayBasedFilter(
                        relay = it,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.THREAD,
                                tags = mapOf("A" to listOf(addressRoot)),
                                since = since,
                            ),
                    ),
                )
            } else {
                emptyList()
            }

        val eventList =
            if (eventRoot != null) {
                listOf(
                    RelayBasedFilter(
                        relay = it,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.THREAD,
                                tags = mapOf("e" to listOf(eventRoot)),
                                since = since,
                            ),
                    ),
                    RelayBasedFilter(
                        relay = it,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.THREAD,
                                tags = mapOf("E" to listOf(eventRoot)),
                                since = since,
                            ),
                    ),
                )
            } else {
                emptyList()
            }

        addressList + eventList
    }
}
