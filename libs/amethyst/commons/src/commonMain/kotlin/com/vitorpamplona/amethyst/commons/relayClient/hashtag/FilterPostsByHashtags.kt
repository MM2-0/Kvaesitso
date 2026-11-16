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
package com.vitorpamplona.amethyst.commons.relayClient.hashtag

import com.vitorpamplona.amethyst.commons.relayClient.nip22Comments.CommentKinds
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtagAlts
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

val PostsByHashtagsKinds =
    listOf(
        TextNoteEvent.KIND,
        ChannelMessageEvent.KIND,
        LongFormContentEvent.KIND,
        PollEvent.KIND,
        LiveActivitiesChatMessageEvent.KIND,
        ClassifiedsEvent.KIND,
        HighlightEvent.KIND,
        WikiArticleEvent.KIND,
        CommentEvent.KIND,
    )

val PostsByHashtagKinds2 =
    listOf(
        InteractiveStorySceneEvent.KIND,
        AudioTrackEvent.KIND,
        AudioHeaderEvent.KIND,
        MusicTrackEvent.KIND,
        MusicPlaylistEvent.KIND,
        PodcastEpisodeEvent.KIND,
        PodcastMetadataEvent.KIND,
        NipTextEvent.KIND,
        ZapPollEvent.KIND,
        EbookEvent.KIND,
        MagazineEvent.KIND,
        MagazineIssueEvent.KIND,
    )

fun filterPostsByHashtags(
    hashtag: String,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val hashtagsToFollowMap = mapOf("t" to hashtagAlts(hashtag).sorted())
    val hashtagScoreMap = mapOf("I" to listOf(HashtagId.toScope(hashtag)))

    return relays.flatMap { relay ->
        val since = since?.get(relay)?.time
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.TAG_FEED,
                        tags = hashtagsToFollowMap,
                        kinds = PostsByHashtagsKinds,
                        limit = 400,
                        since = since,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.TAG_FEED,
                        tags = hashtagsToFollowMap,
                        kinds = PostsByHashtagKinds2,
                        limit = 100,
                        since = since,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.TAG_FEED,
                        tags = hashtagScoreMap,
                        kinds = CommentKinds,
                        limit = 200,
                        since = since,
                    ),
            ),
        )
    }
}
