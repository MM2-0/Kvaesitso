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
package com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import kotlin.math.min

val HomePostsNewThreadKinds1 =
    listOf(
        TextNoteEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ClassifiedsEvent.KIND,
        LongFormContentEvent.KIND,
        HighlightEvent.KIND,
        ZapPollEvent.KIND,
        PollEvent.KIND,
        WikiArticleEvent.KIND,
        AttestationEvent.KIND,
        NipTextEvent.KIND,
        PictureEvent.KIND,
        VideoNormalEvent.KIND,
        VideoShortEvent.KIND,
        AddressableNormalVideoEvent.KIND,
        AddressableShortVideoEvent.KIND,
        // "Going to <event>" from people you follow. Low-volume per author, so it rides the main
        // leg instead of the 5-item rare-kinds leg below, where it would be starved by the others.
        CalendarRSVPEvent.KIND,
        // The other low-volume root posts a follow writes by hand: forum-style threads, the
        // calendar events they organize, fundraising goals, code snippets, stream clips and the
        // issues and pull requests they open on git repositories.
        ThreadEvent.KIND,
        CalendarTimeSlotEvent.KIND,
        CalendarDateSlotEvent.KIND,
        ZapGoalEvent.KIND,
        FundraiserEvent.KIND,
        CodeSnippetEvent.KIND,
        LiveActivitiesClipEvent.KIND,
        GitIssueEvent.KIND,
        GitPullRequestEvent.KIND,
    )

val HomePostsNewThreadKinds2 =
    listOf(
        InteractiveStoryPrologueEvent.KIND,
        ChessGameEvent.KIND,
        LiveChessGameChallengeEvent.KIND,
        LiveChessGameEndEvent.KIND,
        BirdDetectionEvent.KIND,
        BirdexEvent.KIND,
        TorrentEvent.KIND,
        EntityRatingEvent.KIND,
        AttestationRequestEvent.KIND,
        AttestorRecommendationEvent.KIND,
        AttestorProficiencyEvent.KIND,
    )

/**
 * Catalog kinds: low-volume per author but bursty, since a publisher uploads a whole back
 * catalogue (an album, a podcast's episodes, a magazine's issues) at once. They get their own
 * leg so a burst can neither crowd text notes out of the main leg nor fill the rare-kinds leg.
 */
val HomePostsNewThreadKinds3 =
    listOf(
        EbookEvent.KIND,
        MagazineEvent.KIND,
        MagazineIssueEvent.KIND,
        PublicationIndexEvent.KIND,
        AudioTrackEvent.KIND,
        AudioHeaderEvent.KIND,
        MusicTrackEvent.KIND,
        MusicPlaylistEvent.KIND,
        PodcastEpisodeEvent.KIND,
        PodcastMetadataEvent.KIND,
    )

// TorrentCommentEvent is spec-deprecated (replaced by NIP-22) but old torrent threads still use it.
@Suppress("DEPRECATION")
val HomePostsConversationKinds =
    listOf(
        LiveActivitiesChatMessageEvent.KIND,
        CommentEvent.KIND,
        LiveActivitiesEvent.KIND,
        EphemeralChatEvent.KIND,
        VoiceEvent.KIND,
        VoiceReplyEvent.KIND,
        PollResponseEvent.KIND,
        TorrentCommentEvent.KIND,
    )

fun filterNewHomePostsByAuthors(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors.sorted()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.HOME_FEED,
                    kinds = HomePostsNewThreadKinds1,
                    authors = authorList,
                    limit = min(authorList.size * 10, 500),
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.HOME_FEED,
                    kinds = HomePostsNewThreadKinds2,
                    authors = authorList,
                    limit = min(authorList.size * 10, 5),
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.HOME_FEED,
                    kinds = HomePostsNewThreadKinds3,
                    authors = authorList,
                    limit = min(authorList.size * 10, 100),
                    since = since,
                ),
        ),
    )
}

fun filterReplyHomePostsByAuthors(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors.sorted()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.HOME_FEED,
                    kinds = HomePostsConversationKinds,
                    authors = authorList,
                    limit = min(authorList.size * 10, 500),
                    since = since,
                ),
        ),
    )
}

fun filterHomePostsByAuthors(
    authorSet: AuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    sinceBoundaryNew: Long?,
    sinceBoundaryReply: Long?,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterNewHomePostsByAuthors(
                    relay = it.key,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time ?: sinceBoundaryNew,
                ) +
                    filterReplyHomePostsByAuthors(
                        relay = it.key,
                        authors = it.value.authors,
                        since = since?.get(it.key)?.time ?: sinceBoundaryReply,
                    )
            }
        }.flatten()
}

fun filterHomePostsByAuthors(
    authorSet: MutedAuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    sinceBoundaryNew: Long?,
    sinceBoundaryReply: Long?,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterNewHomePostsByAuthors(
                    relay = it.key,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time ?: sinceBoundaryNew,
                ) +
                    filterReplyHomePostsByAuthors(
                        relay = it.key,
                        authors = it.value.authors,
                        since = since?.get(it.key)?.time ?: sinceBoundaryReply,
                    )
            }
        }.flatten()
}
