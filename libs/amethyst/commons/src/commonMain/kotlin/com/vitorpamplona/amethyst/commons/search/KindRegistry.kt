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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.ProductReviewEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent

data class ContentPreset(
    val kinds: List<Int> = emptyList(),
    val pseudoKind: String? = null,
) {
    /** Check if this preset is active given current query state. */
    fun isSelected(
        queryKinds: List<Int>,
        queryPseudoKinds: List<String>,
    ): Boolean =
        if (pseudoKind != null) {
            pseudoKind in queryPseudoKinds
        } else {
            kinds.isNotEmpty() && queryKinds.containsAll(kinds)
        }
}

/**
 * One `kind:` value the picker offers.
 *
 * [kinds] is what the token will actually ask a relay for, so the row can say it — the difference
 * between `kind:video` and `kind:21` is worth seeing before picking. A pseudo-kind asks for no
 * kinds at all: it is a post-filter over whatever else the query matched.
 */
@Immutable
data class KindCandidate(
    val alias: String,
    val kinds: List<Int>,
    val isPseudo: Boolean = false,
)

object KindRegistry {
    val aliases: Map<String, List<Int>> =
        mapOf(
            "note" to listOf(TextNoteEvent.KIND),
            "article" to listOf(LongFormContentEvent.KIND),
            // No `repost` or `profile` here on purpose: both name kinds in
            // [RenderableKinds.NEVER_IN_RESULTS], so the chip drew and the list stayed empty
            // forever. `kind:profile` was the worse of the two — it pinned the scope to Notes,
            // taking away the People scope, which is the only place a profile is ever found.
            "channel" to listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND),
            "live" to listOf(LiveActivitiesEvent.KIND, MeetingSpaceEvent.KIND, MeetingRoomEvent.KIND),
            "community" to listOf(CommunityDefinitionEvent.KIND),
            "wiki" to listOf(WikiArticleEvent.KIND),
            "routstr" to listOf(RoutstrNodeEvent.KIND),
            "ebook" to listOf(EbookEvent.KIND),
            "magazine" to listOf(MagazineEvent.KIND, MagazineIssueEvent.KIND),
            "classified" to listOf(ClassifiedsEvent.KIND),
            // Open Markets around the listings: a seller's (or an event's) collection of products,
            // and a buyer's review of one. `productreview`, not `review`: relays are reviewed too.
            "collection" to listOf(ProductCollectionEvent.KIND),
            "productreview" to listOf(ProductReviewEvent.KIND),
            "geocache" to listOf(GeocacheListingEvent.KIND),
            "highlight" to listOf(HighlightEvent.KIND),
            "poll" to listOf(PollEvent.KIND),
            // The kinds the app's own feeds are windows onto, so a screen that seeds its search
            // gets a chip a reader recognises instead of a bare number.
            "picture" to listOf(PictureEvent.KIND),
            "video" to listOf(VideoNormalEvent.KIND, VideoShortEvent.KIND, AddressableNormalVideoEvent.KIND, AddressableShortVideoEvent.KIND),
            "nsite" to listOf(RootSiteEvent.KIND, NamedSiteEvent.KIND),
            "napplet" to listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND),
            "emoji" to listOf(EmojiPackEvent.KIND),
            "badge" to listOf(BadgeDefinitionEvent.KIND),
            "git" to listOf(GitRepositoryEvent.KIND),
            "nest" to listOf(MeetingSpaceEvent.KIND, MeetingRoomEvent.KIND),
            "app" to listOf(AppDefinitionEvent.KIND),
            "workout" to listOf(WorkoutRecordEvent.KIND),
            "music" to listOf(MusicTrackEvent.KIND),
            "playlist" to listOf(MusicPlaylistEvent.KIND),
            "podcast" to listOf(PodcastMetadataEvent.KIND),
            "episode" to listOf(Podcasting20EpisodeEvent.KIND),
            "software" to listOf(SoftwareApplicationEvent.KIND),
            // Curated sets of those apps (Zapstore's categories).
            "appset" to listOf(AppCurationSetEvent.KIND),
            "followpack" to listOf(StarterPackEvent.KIND),
            "zappoll" to listOf(ZapPollEvent.KIND),
            // `video` is every video kind; these name the two the app gives their own feeds.
            "stories" to listOf(AddressableNormalVideoEvent.KIND, AddressableShortVideoEvent.KIND),
            "short" to listOf(AddressableShortVideoEvent.KIND),
            "longvideo" to listOf(AddressableNormalVideoEvent.KIND),
            // The slots on a calendar, and the calendars that collect them.
            "calendar" to listOf(CalendarTimeSlotEvent.KIND, CalendarDateSlotEvent.KIND),
            "calendarset" to listOf(CalendarCollectionEvent.KIND),
        )

    val pseudoKinds: Set<String> = setOf("reply", "media")

    /**
     * [aliases], widest group first — the order [tokenize] has to try them in, sorted once.
     *
     * It ran `sortedByDescending` per call, and the calls are not rare: every chip the field
     * draws asks for the name of its kind on every recomposition, and every serialization of a
     * query goes through here too.
     */
    private val aliasesByWidestFirst = aliases.entries.sortedByDescending { it.value.size }

    val presets: Map<String, ContentPreset> =
        mapOf(
            "Notes" to ContentPreset(kinds = listOf(TextNoteEvent.KIND)),
            "Articles" to ContentPreset(kinds = listOf(LongFormContentEvent.KIND)),
            "Media" to ContentPreset(pseudoKind = "media"),
            "Channels" to ContentPreset(kinds = listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND)),
            "Communities" to ContentPreset(kinds = listOf(CommunityDefinitionEvent.KIND)),
            "Wiki" to ContentPreset(kinds = listOf(WikiArticleEvent.KIND)),
            "Polls" to ContentPreset(kinds = listOf(PollEvent.KIND)),
        )

    fun resolve(alias: String): List<Int>? = aliases[alias.lowercase()]

    fun isPseudoKind(alias: String): Boolean = alias.lowercase() in pseudoKinds

    fun nameFor(kind: Int): String? = aliases.entries.find { kind in it.value }?.key

    /** Is this exactly one name the registry knows, rather than a prefix of one? */
    fun isExactAlias(value: String?): Boolean {
        val v = value?.lowercase() ?: return false
        return v in aliases || v in pseudoKinds
    }

    /**
     * The `kind:` values worth offering for a half-written one, aliases before pseudo-kinds and
     * alphabetical within each.
     *
     * The whole vocabulary is here in the registry, which is what makes this picker different
     * from the others: people and groups are account-scoped, relay-backed questions, while
     * "which kinds are there" is a constant. An empty [prefix] offers everything, because a bare
     * `kind:` is a reader asking what the options are.
     */
    fun candidates(prefix: String): List<KindCandidate> {
        val p = prefix.lowercase()
        val named =
            aliases.keys
                .filter { it.startsWith(p) }
                .sorted()
                .map { KindCandidate(it, resolve(it).orEmpty()) }
        val pseudo =
            pseudoKinds
                .filter { it.startsWith(p) }
                .sorted()
                .map { KindCandidate(it, emptyList(), isPseudo = true) }
        return named + pseudo
    }

    /**
     * A kind window written back as `kind:` token values: the aliases that cover it, plus a bare
     * number for every kind no alias claims.
     *
     * Aliases match **whole** and one per group, which is the difference between this and calling
     * [nameFor] per kind. `channel` is 40 *and* 41, so per-kind naming wrote it twice; and a
     * window holding only 30312 is not `kind:live` (30311, 30312, 30313) — writing it as one
     * would widen the query a little more on every round trip through the field. Larger groups
     * are tried first, so the most specific name that actually fits wins.
     *
     * Tokens come back in the order [kinds] first mentions them, so reading the result back gives
     * the same list rather than one shuffled by which alias happened to be the longest.
     */
    fun tokenize(kinds: List<Int>): List<String> {
        if (kinds.isEmpty()) return emptyList()
        val remaining = kinds.toMutableList()
        val tokens = mutableListOf<Pair<Int, String>>()
        aliasesByWidestFirst
            .forEach { (alias, group) ->
                if (remaining.containsAll(group)) {
                    tokens.add(kinds.indexOfFirst { it in group } to alias)
                    remaining.removeAll(group)
                }
            }
        remaining.forEach { kind -> tokens.add(kinds.indexOf(kind) to kind.toString()) }
        return tokens.sortedBy { it.first }.map { it.second }
    }
}
