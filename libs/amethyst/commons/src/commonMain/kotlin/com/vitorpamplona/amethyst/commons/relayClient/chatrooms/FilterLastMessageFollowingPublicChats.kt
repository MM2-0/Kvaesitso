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
package com.vitorpamplona.amethyst.commons.relayClient.chatrooms

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.utils.mapOfSet

fun filterLastMessageFollowingPublicChats(
    followingChannels: Set<HexKey>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter>? {
    if (followingChannels.isEmpty()) return null

    val relayRoomDTags =
        mapOfSet {
            followingChannels.forEach { channelId ->
                val relays =
                    LocalCache.getPublicChatChannelIfExists(channelId)?.relays()?.ifEmpty { null }
                        ?: LocalCache.relayHints.hintsForEvent(channelId).ifEmpty { null }
                        ?: Constants.eventFinderRelays

                relays.forEach { relayUrl ->
                    add(relayUrl, channelId)
                }
            }
        }

    return relayRoomDTags
        .map {
            listOf(
                RelayBasedFilter(
                    // Metadata comes from any relay
                    relay = it.key,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.PUBLIC_CHATS,
                            entityIds = it.value.sorted(),
                            kinds = listOf(ChannelMetadataEvent.KIND),
                            tags = mapOf("e" to it.value.sorted()),
                            since = since?.get(it.key)?.time,
                            limit = 1,
                        ),
                ),
                RelayBasedFilter(
                    relay = it.key,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.PUBLIC_CHATS,
                            entityIds = it.value.sorted(),
                            kinds = listOf(ChannelMessageEvent.KIND),
                            tags = mapOf("e" to it.value.sorted()),
                            since = since?.get(it.key)?.time,
                            // Remember to consider spam that is being removed from the UI
                            limit = 100,
                        ),
                ),
            )
        }.flatten()
}
