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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.ProductReviewEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent

/**
 * The kinds a search can put on screen, in one place.
 *
 * A search that names no kind still cannot ask for "everything": a relay would answer with events
 * Amethyst has no card for, and the reader would scroll past blanks. So the window is listed, and
 * — because this list decides what a query reaches — it was previously listed **three times**: in
 * the Android relay subscription, again in desktop's filter factory, and inverted as a denylist in
 * the local cache scan. They drifted, as three copies do. Desktop's third group was missing the
 * calendar slots and code snippets; the local scan had no kind window at all, so local and relay
 * results for the same query came from different sets.
 *
 * ## What is in it
 *
 * Every kind here is one Amethyst renders *and* — with three named exceptions — one Quartz
 * declares searchable, which `RenderableKindsTest` checks against
 * [com.vitorpamplona.quartz.nip50Search.SearchableKinds]. That check is the point of the file:
 * asking a relay for a kind whose text nothing indexes is a filter slot spent on a result that
 * cannot come back, and *not* asking for a kind that is both searchable and renderable is a hole
 * the reader experiences as "the search does not find my pictures". Both happened. The audit that
 * produced this list found fifteen searchable, renderable kinds absent — pictures, every video
 * kind, workouts, git repos, sites, napplets, meetings and calendar events among them.
 */
object RenderableKinds {
    /**
     * Kinds asked for despite Quartz not indexing their text.
     *
     * They are here because [ALL] answers "what can a result be", not "what can a text query
     * match", and a chips-only query (`from:npub1… since:2024-01-01`) carries no text for anything
     * to be indexed against. A relay that indexes raw `content` may still match them, and
     * `EventSearchMatcher` does locally, so they are not dead weight — just not guaranteed.
     */
    val MATCHES_ON_CONTENT_ONLY =
        setOf(
            PinListEvent.KIND,
            PollResponseEvent.KIND,
            NNSEvent.KIND,
        )

    /**
     * Kinds a search result can never be, whatever the query says.
     *
     * Reader policy rather than protocol: a repost carries no words of its own, a reaction is one
     * emoji, a zap is a receipt, and a profile belongs to the People scope. They are dropped from
     * results even when a `kind:` token asks for them by name — which is why the list has to be
     * readable from here rather than living only in the cache's scan. It was an `is`-chain over
     * twelve event classes in `CacheSearch`, and nothing stopped the `kind:` vocabulary from
     * offering a name for one of them: `kind:repost` and `kind:profile` were both offered, and
     * both were guaranteed to return nothing.
     *
     * Not the same as absence from [ALL]. A kind missing from [ALL] is merely outside the default
     * window and comes back the moment a query names it; a kind here never comes back at all.
     */
    val NEVER_IN_RESULTS =
        setOf(
            MetadataEvent.KIND,
            ContactListEvent.KIND,
            DeletionRequestEvent.KIND,
            RepostEvent.KIND,
            ReactionEvent.KIND,
            GenericRepostEvent.KIND,
            RequestToVanishEvent.KIND,
            FileMetadataEvent.KIND,
            CommunityPostApprovalEvent.KIND,
            ZapRequestEvent.KIND,
            ZapReceiptEvent.KIND,
            AppSpecificDataEvent.KIND,
        )

    /**
     * Most relays reject a filter naming more kinds than this, which is the only reason [GROUPS]
     * exists. It is a limit on the wire, not on what can be searched.
     */
    const val MAX_KINDS_PER_FILTER = 16

    /** Every kind, for a caller with no per-filter limit — the local cache scan. */
    val ALL =
        listOf(
            // notes and long-form
            TextNoteEvent.KIND,
            CommentEvent.KIND,
            PublicMessageEvent.KIND,
            LongFormContentEvent.KIND,
            WikiArticleEvent.KIND,
            HighlightEvent.KIND,
            CodeSnippetEvent.KIND,
            NipTextEvent.KIND,
            EbookEvent.KIND,
            MagazineEvent.KIND,
            MagazineIssueEvent.KIND,
            // pictures, video, audio
            PictureEvent.KIND,
            VideoNormalEvent.KIND,
            VideoShortEvent.KIND,
            AddressableNormalVideoEvent.KIND,
            AddressableShortVideoEvent.KIND,
            // A video list has a card of its own, so by the rule the omission list states — in
            // ALL when Amethyst can render it — it belongs in the window. Its title and
            // description are what Quartz indexes, which is exactly what a reader would search.
            VideoCurationSetEvent.KIND,
            AudioHeaderEvent.KIND,
            AudioTrackEvent.KIND,
            MusicTrackEvent.KIND,
            MusicPlaylistEvent.KIND,
            PodcastEpisodeEvent.KIND,
            PodcastMetadataEvent.KIND,
            // drss.io's generated shows, and the lists, folders and provenance notes of its reader
            DrssShowEvent.KIND,
            DrssListFolderEvent.KIND,
            DrssFeedListEvent.KIND,
            DrssEventListEvent.KIND,
            DrssProvenanceFolderEvent.KIND,
            DrssProvenanceEvent.KIND,
            // interactive stories
            InteractiveStoryPrologueEvent.KIND,
            InteractiveStorySceneEvent.KIND,
            // polls
            PollEvent.KIND,
            PollResponseEvent.KIND,
            ZapPollEvent.KIND,
            // channels, streams and rooms
            ChannelCreateEvent.KIND,
            ChannelMetadataEvent.KIND,
            LiveActivitiesEvent.KIND,
            MeetingSpaceEvent.KIND,
            MeetingRoomEvent.KIND,
            // communities and lists
            CommunityDefinitionEvent.KIND,
            FollowSetEvent.KIND,
            StarterPackEvent.KIND,
            BookmarkListEvent.KIND,
            OldBookmarkListEvent.KIND,
            PinListEvent.KIND,
            // calendar
            CalendarDateSlotEvent.KIND,
            CalendarTimeSlotEvent.KIND,
            CalendarCollectionEvent.KIND,
            // marketplace, apps, code and sites. Open Markets collections and product reviews have
            // cards and are what a shopper searches for; shipping options are reached through the
            // product that names them.
            ClassifiedsEvent.KIND,
            ProductCollectionEvent.KIND,
            ProductReviewEvent.KIND,
            SoftwareApplicationEvent.KIND,
            AppCurationSetEvent.KIND,
            GitRepositoryEvent.KIND,
            RootSiteEvent.KIND,
            NamedSiteEvent.KIND,
            RootNappletEvent.KIND,
            NamedNappletEvent.KIND,
            // geocaches
            GeocacheListingEvent.KIND,
            GeocacheFoundLogEvent.KIND,
            // Kanban boards and cards (titles and descriptions), and the WalletScrutiny build
            // verifications that share 30301 (their reports). The encrypted planner tasks also on
            // 30301 index nothing and are never cached, so they cannot surface as a blank row.
            KanbanBoardEvent.KIND,
            KanbanCardEvent.KIND,
            // everything else with a card
            BadgeDefinitionEvent.KIND,
            EmojiPackEvent.KIND,
            NNSEvent.KIND,
            WorkoutRecordEvent.KIND,
            // Routstr nodes: a card with "Use for The Observer"; their name and about are indexed.
            RoutstrNodeEvent.KIND,
        )

    /**
     * [ALL] split into filters a relay will accept, balanced rather than greedy so growth adds a
     * kind to each group instead of leaving a group of one behind.
     */
    val GROUPS = ALL.balancedChunks(MAX_KINDS_PER_FILTER)

    private fun List<Int>.balancedChunks(max: Int): List<List<Int>> {
        val groups = (size + max - 1) / max
        if (groups <= 1) return listOf(this)
        val base = size / groups
        val remainder = size % groups
        var from = 0
        return (0 until groups).map { index ->
            val take = base + if (index < remainder) 1 else 0
            subList(from, from + take).toList().also { from += take }
        }
    }
}
