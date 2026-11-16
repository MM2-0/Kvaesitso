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
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent

// A single-podcast screen fetches everything authored by the show's pubkey, across both drafts:
// NIP-F4 metadata (kind 10154) + episodes (kind 54), and Podcasting-2.0 episodes (kind 30054) +
// trailers (kind 30055). In both models the show key authors its own episodes and trailers.
private val OnePodcastKinds =
    listOf(
        PodcastMetadataEvent.KIND,
        PodcastEpisodeEvent.KIND,
        Podcasting20EpisodeEvent.KIND,
        Podcasting20TrailerEvent.KIND,
    )

fun filterOnePodcast(
    cache: ICacheProvider,
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    // Outbox relays for the podcast key are where the show publishes. Fall back to any relay
    // we've seen the key on plus stored hints when no NIP-65 list is known.
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: (user.allUsedRelays() + cache.relayHints.hintsForKey(user.pubkeyHex))

    return relays.flatMap { relay ->
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.MEDIA_FEED,
                        kinds = OnePodcastKinds,
                        authors = listOf(user.pubkeyHex),
                        limit = 500,
                        since = since?.get(relay)?.time,
                    ),
            ),
            // Podcasting-2.0 show metadata rides on the generic NIP-78 app-data kind (30078),
            // so it needs its own #d=podcast-metadata filter rather than a bare kind match.
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.MEDIA_FEED,
                        kinds = listOf(AppSpecificDataEvent.KIND),
                        authors = listOf(user.pubkeyHex),
                        tags = mapOf("d" to listOf(Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG)),
                        limit = 1,
                        since = since?.get(relay)?.time,
                    ),
            ),
        )
    }
}
