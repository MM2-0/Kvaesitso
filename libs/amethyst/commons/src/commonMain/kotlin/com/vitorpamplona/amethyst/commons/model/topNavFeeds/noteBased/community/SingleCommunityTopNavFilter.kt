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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.OutboxRelayLoader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Immutable
class SingleCommunityTopNavFilter(
    val community: String,
    val authors: Set<String>?,
    val relays: Set<NormalizedRelayUrl>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey) = authors == null || pubkey in authors

    // The moderators whose own relays this feed reads: only when the community names no relays of its
    // own. Routes both the REQ (toPerRelayFlow) and the relay-list loading (outboxAuthors).
    private val authorsByOutbox: Set<HexKey>? = if (relays.isEmpty()) authors else null

    override fun outboxAuthors(): Set<HexKey> = authorsByOutbox.orEmpty()

    override fun match(noteEvent: Event): Boolean =
        if (noteEvent is LiveActivitiesEvent) {
            (authors != null && noteEvent.participantsIntersect(authors)) || noteEvent.isForCommunity(community)
        } else {
            // a comment is in the community by its NIP-22 root `A`, which nested replies keep.
            (authors != null && noteEvent.pubKey in authors) || noteEvent.isForCommunity(community)
        }

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<SingleCommunityTopNavPerRelayFilterSet> {
        // relay field takes priority
        if (relays.isNotEmpty()) {
            return blockedRelays.map { blocked ->
                SingleCommunityTopNavPerRelayFilterSet(
                    relays.minus(blocked).associateWith {
                        SingleCommunityTopNavPerRelayFilter(community, authors)
                    },
                )
            }
        }

        if (authorsByOutbox != null) {
            // go by authors
            val authorsPerRelay = OutboxRelayLoader().toAuthorsPerRelayFlow(authorsByOutbox, cache) { it }

            return combine(authorsPerRelay, blockedRelays) { authorsPerRelay, blocked ->
                SingleCommunityTopNavPerRelayFilterSet(
                    authorsPerRelay.minus(blocked).mapValues {
                        SingleCommunityTopNavPerRelayFilter(community, it.value)
                    },
                )
            }
        }

        // go by hints
        return blockedRelays.map { blocked ->
            SingleCommunityTopNavPerRelayFilterSet(
                cache.relayHints.hintsForAddress(community).minus(blocked).associateWith {
                    SingleCommunityTopNavPerRelayFilter(community, authors)
                },
            )
        }
    }

    override fun startValue(cache: ICacheProvider): SingleCommunityTopNavPerRelayFilterSet {
        // relay field takes priority
        if (relays.isNotEmpty()) {
            return SingleCommunityTopNavPerRelayFilterSet(
                relays.minus(blockedRelays.value).associateWith {
                    SingleCommunityTopNavPerRelayFilter(community, authors)
                },
            )
        }

        if (authorsByOutbox != null) {
            // go by authors
            val authorsPerRelay = OutboxRelayLoader().authorsPerRelaySnapshot(authorsByOutbox, cache) { it }

            return SingleCommunityTopNavPerRelayFilterSet(
                authorsPerRelay.minus(blockedRelays.value).mapValues {
                    SingleCommunityTopNavPerRelayFilter(community, it.value)
                },
            )
        }

        // go by hints
        return SingleCommunityTopNavPerRelayFilterSet(
            cache.relayHints.hintsForAddress(community).minus(blockedRelays.value).associateWith {
                SingleCommunityTopNavPerRelayFilter(community, authors)
            },
        )
    }
}
