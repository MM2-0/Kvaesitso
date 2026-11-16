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
package com.vitorpamplona.amethyst.commons.relayClient.search

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.dialableWriteRelays
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

val MetadataKindList = listOf(MetadataEvent.KIND)

fun filterByAuthor(
    cache: ICacheProvider,
    pubKey: HexKey,
    indexRelays: Set<NormalizedRelayUrl>,
    defaultRelays: Set<NormalizedRelayUrl>,
) = cache.checkGetOrCreateUser(pubKey)?.let { key ->
    val perRelay =
        mapOfSet {
            val relays =
                key.authorRelayList()?.dialableWriteRelays()
                    ?: (key.allUsedRelays() + cache.relayHints.hintsForKey(key.pubkeyHex) + indexRelays).ifEmpty { null }
                    ?: defaultRelays.toList()

            relays.forEach {
                add(it, key.pubkeyHex)
            }
        }

    val myUser = listOf(pubKey)

    perRelay.map {
        RelayBasedFilter(
            relay = it.key,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.SEARCH,
                    kinds = MetadataKindList,
                    authors = myUser,
                ),
        )
    }
} ?: emptyList()
