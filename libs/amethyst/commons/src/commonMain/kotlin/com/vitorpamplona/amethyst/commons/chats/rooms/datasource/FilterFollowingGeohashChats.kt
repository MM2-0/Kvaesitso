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
package com.vitorpamplona.amethyst.commons.chats.rooms.datasource

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.commons.service.georelay.GeohashRelays
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * REQ for the user's joined geohash location channels: for each cell, subscribe
 * to its kind-20000 messages on the relays nearest that cell
 * ([GeohashRelays.closestRelays]) — the same rendezvous set Bitchat uses. One
 * [RelayBasedFilter] per (cell, relay). Presence (kind 20001) is left to the live
 * chat screen so it never becomes a room's last message.
 */
fun filterFollowingGeohashChats(
    geohashes: Set<String>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter>? {
    if (geohashes.isEmpty()) return null

    // Group cells by their nearest relays, so cells that share a relay collapse into a single filter
    // (g = [all those cells]) instead of one REQ per (cell, relay).
    val cellsByRelay = LinkedHashMap<NormalizedRelayUrl, MutableList<String>>()
    geohashes.forEach { geohash ->
        GeohashRelays.closestRelays(geohash).forEach { relay ->
            cellsByRelay.getOrPut(relay) { mutableListOf() }.add(geohash)
        }
    }

    return cellsByRelay.map { (relay, cells) ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.GEOHASH_CHATS,
                    // One filter per relay carries every cell that relay serves, so all of them ride
                    // along and the screen fans them into a row each.
                    entityIds = cells.sorted(),
                    kinds = listOf(GeohashChatEvent.KIND),
                    tags = mapOf("g" to cells),
                    limit = 100,
                    since = since?.get(relay)?.time,
                ),
        )
    }
}
