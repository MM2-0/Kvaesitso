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

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_PIN_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/**
 * The relay-signed metadata for a NIP-29 group (name/picture/about + admin,
 * member and role lists), addressed by the group id (`d` tag) and pinned to the
 * group's host relay. The relay signs these with its own key, so a single-relay
 * query scoped by `#d` returns exactly this group's directory.
 *
 * The 39000-39003 metadata block and the 39005 pin list go out as **two separate filters**: relay29-family
 * relays (0xchat's included) reject a filter that mixes them and drop the whole REQ, which would leave the
 * group with no name, no roster and no membership. See
 * [RELAY_GROUP_PIN_KINDS].
 */
fun filterRelayGroupState(
    channel: RelayGroupChannel,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays = channel.relays().toSet()
    val scope = mapOf("d" to listOf(channel.groupId.id))
    val directory =
        relays.flatMap {
            val floor = since?.get(it)?.time
            listOf(
                RelayBasedFilter(relay = it, filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, kinds = RELAY_GROUP_METADATA_KINDS, tags = scope, since = floor)),
                RelayBasedFilter(relay = it, filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, kinds = RELAY_GROUP_PIN_KINDS, tags = scope, since = floor)),
            )
        }

    // Back-fill the bodies of pinned messages by id from the host relay. A pin can point at a
    // message older than the 200-item timeline window, which the `h`-scoped timeline filter would
    // never return — without this the pin bar shows a blank preview and can't jump to it. No `since`:
    // pinned events are immutable, so we want them regardless of age.
    val pinnedIds = channel.pinnedEventIds
    val pins =
        if (pinnedIds.isEmpty()) {
            emptyList()
        } else {
            relays.map { RelayBasedFilter(relay = it, filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, ids = pinnedIds)) }
        }

    // Same back-fill for `a` pins (addressable events, NIP-29 #2416): one kind+author filter per
    // (kind, author) pair carrying every pinned `d` of that pair — the union of the per-address
    // filters, in as few filters as the pins allow. No `since` either — the pin stays valid however
    // old the latest version is.
    val pinnedAddresses = channel.pinnedAddresses
    val addressPins =
        if (pinnedAddresses.isEmpty()) {
            emptyList()
        } else {
            val byKindAndAuthor = pinnedAddresses.groupBy({ it.kind to it.pubKeyHex }, { it.dTag })
            relays.flatMap { relay ->
                byKindAndAuthor.map { (kindAndAuthor, dTags) ->
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.RELAY_GROUPS,
                                kinds = listOf(kindAndAuthor.first),
                                authors = listOf(kindAndAuthor.second),
                                tags = mapOf("d" to dTags.distinct()),
                            ),
                    )
                }
            }
        }

    return directory + pins + addressPins
}
