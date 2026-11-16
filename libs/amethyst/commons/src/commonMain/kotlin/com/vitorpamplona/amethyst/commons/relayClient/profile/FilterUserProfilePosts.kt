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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.commons.relayClient.profile

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent

val UserProfilePostKinds1 =
    listOf(
        TextNoteEvent.KIND,
        GenericRepostEvent.KIND,
        RepostEvent.KIND,
        LongFormContentEvent.KIND,
        PollEvent.KIND,
        HighlightEvent.KIND,
        WikiArticleEvent.KIND,
        VoiceEvent.KIND,
        PublicMessageEvent.KIND,
    )

val UserProfilePostKinds2 =
    listOf(
        TorrentEvent.KIND,
        TorrentCommentEvent.KIND,
        NipTextEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        CommentEvent.KIND,
        VoiceReplyEvent.KIND,
        ZapPollEvent.KIND,
        PinListEvent.KIND,
        AttestationEvent.KIND,
        BirdDetectionEvent.KIND,
        BirdexEvent.KIND,
        EntityRatingEvent.KIND,
        PublicationIndexEvent.KIND,
        EbookEvent.KIND,
        MagazineEvent.KIND,
        MagazineIssueEvent.KIND,
    )

// NIP-5A nsites (15128/35128) and NIP-5D napplets (15129/35129) the user publishes, surfaced in
// the profile "Apps" tab, plus the NIP-34 git repository announcements (30617) behind the
// profile "Repos" tab. Fetching them only populates the cache; execution stays sandboxed.
val UserProfileAppKinds =
    listOf(
        RootSiteEvent.KIND,
        NamedSiteEvent.KIND,
        RootNappletEvent.KIND,
        NamedNappletEvent.KIND,
        GitRepositoryEvent.KIND,
    )

// The user's Jester chess events (kind 30), for the games they ended. Relays cannot filter on
// the content, so this takes their latest moves and the profile keeps only the endings. Never
// bare kind 30 without an author: NKBIP-03 shares the kind.
val UserProfileChessKinds = listOf(JesterProtocol.KIND)

fun filterUserProfilePosts(
    cache: ICacheProvider,
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: (user.allUsedRelays() + cache.relayHints.hintsForKey(user.pubkeyHex))

    return relays.flatMap { relay ->
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = UserProfilePostKinds1,
                        authors = listOf(user.pubkeyHex),
                        limit = 500,
                        since = since?.get(relay)?.time,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = UserProfilePostKinds2,
                        authors = listOf(user.pubkeyHex),
                        limit = 50,
                        since = since?.get(relay)?.time,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = UserProfileAppKinds,
                        authors = listOf(user.pubkeyHex),
                        limit = 50,
                        since = since?.get(relay)?.time,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = UserProfileChessKinds,
                        authors = listOf(user.pubkeyHex),
                        limit = 100,
                        since = since?.get(relay)?.time,
                    ),
            ),
        )
    }
}
