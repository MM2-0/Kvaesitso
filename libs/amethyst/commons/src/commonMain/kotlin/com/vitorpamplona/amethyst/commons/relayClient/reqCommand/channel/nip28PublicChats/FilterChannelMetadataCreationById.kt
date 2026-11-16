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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.nip28PublicChats

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.ChannelFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val filterMissingPublicChannelsByIdKinds = listOf(ChannelCreateEvent.KIND)

fun filterMissingChannelsById(keys: List<ChannelFinderQueryState>): List<RelayBasedFilter> {
    val relayPerChannel =
        mapOfSet {
            keys.forEach { key ->
                if (key.channel is PublicChatChannel && key.channel.event == null) {
                    val searchRelays =
                        key.account.searchRelayList.flow.value
                            .ifEmpty { DefaultSearchRelayList }
                    val indexerRelays =
                        key.account.indexerRelayList.flow.value
                            .ifEmpty { DefaultIndexerRelayList }

                    (key.channel.relays() + searchRelays + indexerRelays).forEach {
                        add(it, key.channel.idHex)
                    }
                }
            }
        }

    return relayPerChannel.map { (relay, channelIds) ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.PUBLIC_CHATS,
                    entityIds = channelIds.sorted(),
                    kinds = filterMissingPublicChannelsByIdKinds,
                    ids = channelIds.sorted(),
                ),
        )
    }
}
