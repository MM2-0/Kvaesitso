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
package com.vitorpamplona.amethyst.commons.home.dal

import com.vitorpamplona.amethyst.commons.audio.hasPlayableAudio
import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.isRenderableRepost
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.isLatestAnswerIn
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.latestRsvpAnswers
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
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
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

class HomeNewThreadFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    companion object {
        val ADDRESSABLE_KINDS =
            listOf(
                AudioTrackEvent.KIND,
                MusicTrackEvent.KIND,
                MusicPlaylistEvent.KIND,
                PodcastMetadataEvent.KIND,
                InteractiveStoryPrologueEvent.KIND,
                WikiArticleEvent.KIND,
                ClassifiedsEvent.KIND,
                FundraiserEvent.KIND,
                EbookEvent.KIND,
                MagazineEvent.KIND,
                MagazineIssueEvent.KIND,
                BirdexEvent.KIND,
                LongFormContentEvent.KIND,
                LiveChessGameEndEvent.KIND,
                AttestationEvent.KIND,
                AttestationRequestEvent.KIND,
                AttestorRecommendationEvent.KIND,
                AttestorProficiencyEvent.KIND,
                AddressableNormalVideoEvent.KIND,
                AddressableShortVideoEvent.KIND,
                EntityRatingEvent.KIND,
                CalendarRSVPEvent.KIND,
                CalendarTimeSlotEvent.KIND,
                CalendarDateSlotEvent.KIND,
                PublicationIndexEvent.KIND,
            )
    }

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultHomeFollowList.value

    override fun showHiddenKey(): Boolean =
        account.liveHomeFollowLists.value is MutedAuthorsByOutboxTopNavFilter ||
            account.liveHomeFollowLists.value is MutedAuthorsByProxyTopNavFilter

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)
        val disabledKinds = HomeFeedType.disabledKinds(account.settings.enabledHomeFeedTypes.value)
        val latestRsvps = lazy(LazyThreadSafetyMode.NONE) { cachedRsvpAnswers() }

        val notes =
            LocalCache.notes.filterIntoSet { _, note ->
                // Avoids processing addressables twice.
                (note.event?.kind ?: 99999) < 10000 && acceptableEvent(note, filterParams, disabledKinds, latestRsvps)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet(
                kinds = ADDRESSABLE_KINDS,
            ) { _, note ->
                acceptableEvent(note, filterParams, disabledKinds, latestRsvps)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    /**
     * The additive merge only ever adds, so a "Going" card already on screen would outlive the
     * author changing their mind: when a batch carries RSVPs, the old list first loses every
     * RSVP that is no longer its author's latest answer.
     */
    override fun updateListWith(
        oldList: List<Note>,
        newItems: Set<Note>,
    ): List<Note> {
        if (newItems.none { it.event is CalendarRSVPEvent }) return super.updateListWith(oldList, newItems)
        val latest = cachedRsvpAnswers()
        val stillCurrent = oldList.filter { (it.event as? CalendarRSVPEvent)?.isLatestAnswerIn(latest) ?: true }
        return super.updateListWith(stillCurrent, newItems)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)
        val disabledKinds = HomeFeedType.disabledKinds(account.settings.enabledHomeFeedTypes.value)
        val latestRsvps = lazy(LazyThreadSafetyMode.NONE) { cachedRsvpAnswers() }

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, filterParams, disabledKinds, latestRsvps)
        }
    }

    private fun acceptableEvent(
        it: Note,
        filterParams: FilterByListParams,
        disabledKinds: Set<Int>,
        latestRsvps: Lazy<Map<Pair<Address, HexKey>, CalendarRSVPEvent>>,
    ): Boolean {
        val noteEvent = it.event ?: return false
        if (noteEvent.kind in disabledKinds) return false
        return (
            noteEvent is TextNoteEvent ||
                noteEvent is ClassifiedsEvent ||
                noteEvent is FundraiserEvent ||
                noteEvent is EbookEvent ||
                noteEvent is MagazineEvent ||
                noteEvent is MagazineIssueEvent ||
                noteEvent is BirdexEvent ||
                noteEvent is BirdDetectionEvent ||
                noteEvent.isRenderableRepost() ||
                (noteEvent is LongFormContentEvent && noteEvent.content.isNotEmpty()) ||
                (noteEvent is WikiArticleEvent && noteEvent.content.isNotEmpty()) ||
                noteEvent is ZapPollEvent ||
                noteEvent is PollEvent ||
                noteEvent is HighlightEvent ||
                noteEvent is InteractiveStoryPrologueEvent ||
                noteEvent is CommentEvent ||
                (noteEvent is AudioTrackEvent && noteEvent.hasPlayableAudio()) ||
                noteEvent is MusicTrackEvent ||
                noteEvent is MusicPlaylistEvent ||
                noteEvent is PodcastEpisodeEvent ||
                noteEvent is PodcastMetadataEvent ||
                noteEvent is VoiceEvent ||
                noteEvent is AudioHeaderEvent ||
                noteEvent is ChessGameEvent ||
                noteEvent is LiveChessGameEndEvent ||
                noteEvent is PictureEvent ||
                noteEvent is VideoNormalEvent ||
                noteEvent is VideoShortEvent ||
                noteEvent is AddressableNormalVideoEvent ||
                noteEvent is AddressableShortVideoEvent ||
                noteEvent is TorrentEvent ||
                noteEvent is AttestationEvent ||
                noteEvent is AttestationRequestEvent ||
                noteEvent is AttestorRecommendationEvent ||
                noteEvent is AttestorProficiencyEvent ||
                // A rating with nothing to point at cannot be rendered.
                (noteEvent is EntityRatingEvent && noteEvent.hasTarget()) ||
                noteEvent is ThreadEvent ||
                noteEvent is CalendarTimeSlotEvent ||
                noteEvent is CalendarDateSlotEvent ||
                noteEvent is ZapGoalEvent ||
                noteEvent is CodeSnippetEvent ||
                noteEvent is LiveActivitiesClipEvent ||
                noteEvent is GitIssueEvent ||
                noteEvent is GitPullRequestEvent ||
                noteEvent is PublicationIndexEvent ||
                noteEvent.isGoingRsvp(latestRsvps)
        ) &&
            filterParams.match(noteEvent, it.relays) &&
            it.isNewThread()
    }

    override fun sort(items: Set<Note>): List<Note> =
        items
            .distinctBy {
                if (it.event is RepostEvent || it.event is GenericRepostEvent) {
                    it.replyTo?.lastOrNull()?.idHex ?: it.idHex // only the most recent repost per feed.
                } else {
                    it.idHex
                }
            }.sortedByDefaultFeedOrder()
}

/**
 * Only "going" RSVPs reach the home feed: the point is to see where your people are going.
 * Maybes and declines are still visible on the appointment itself, but as posts they are noise.
 * And only the author's current answer: someone who said "going" and later "can't go" under a
 * new d tag still has the old RSVP live, and it must not keep saying they are going.
 */
private fun Event.isGoingRsvp(latestRsvps: Lazy<Map<Pair<Address, HexKey>, CalendarRSVPEvent>>): Boolean {
    if (this !is CalendarRSVPEvent || status() != RSVPStatusTag.STATUS.ACCEPTED) return false
    // An `a` tag at anything but an appointment has no event card to draw next to "Going".
    val target = calendarEventAddress() ?: return false
    if (target.kind != CalendarTimeSlotEvent.KIND && target.kind != CalendarDateSlotEvent.KIND) return false
    return isLatestAnswerIn(latestRsvps.value)
}

// Built from the cache's RSVP kind index, and only when a pass actually meets an RSVP.
private fun cachedRsvpAnswers(): Map<Pair<Address, HexKey>, CalendarRSVPEvent> = latestRsvpAnswers(LocalCache.addressables.filterIntoSet(CalendarRSVPEvent.KIND).mapNotNull { it.event as? CalendarRSVPEvent })
