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
package com.vitorpamplona.amethyst.commons.relayClient.podcasts

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent

/**
 * REQ for the logged-in creator's own Podcasting-2.0 catalog, so the authoring hub reliably shows
 * their show, episodes and trailers even on a fresh install (rather than only what happens to be in
 * [LocalCache]). Queried on the creator's own outbox relays.
 *
 * Two separate filters per relay, and they can't be merged:
 * - episodes (kind 30054) + trailers (kind 30055) by author, with no tag constraint; and
 * - the show metadata (kind 30078) constrained to `#d=["podcast-metadata"]`. That NIP-78 app-data
 *   kind is heavily overloaded, so without the `#d` constraint this REQ would pull every app's data
 *   (including the user's private settings) for the pubkey. The constraint must stay off the episodes
 *   filter, since each episode/trailer carries its own `d` tag — a combined `#d` would match nothing.
 */
fun filterMyPodcast(
    cache: ICacheProvider,
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: (user.allUsedRelays() + cache.relayHints.hintsForKey(user.pubkeyHex))

    val authors = listOf(user.pubkeyHex)

    return relays.flatMap { relay ->
        val sinceTime = since?.get(relay)?.time
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.MEDIA_FEED,
                        authors = authors,
                        kinds = listOf(Podcasting20EpisodeEvent.KIND, Podcasting20TrailerEvent.KIND),
                        since = sinceTime,
                        limit = 200,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.MEDIA_FEED,
                        authors = authors,
                        kinds = listOf(AppSpecificDataEvent.KIND),
                        tags = mapOf("d" to listOf(Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG)),
                        since = sinceTime,
                        limit = 10,
                    ),
            ),
        )
    }
}
