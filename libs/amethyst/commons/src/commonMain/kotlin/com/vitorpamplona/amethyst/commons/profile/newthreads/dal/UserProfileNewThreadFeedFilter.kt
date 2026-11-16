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
package com.vitorpamplona.amethyst.commons.profile.newthreads.dal

import com.vitorpamplona.amethyst.commons.audio.hasPlayableAudio
import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.isRenderableRepost
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip64Chess.ending
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
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

class UserProfileNewThreadFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + user.pubkeyHex

    override fun feed(): List<Note> {
        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                it !is AddressableNote && it.event !is AddressableEvent && acceptableEvent(it)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet { _, it ->
                acceptableEvent(it)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> = collection.filterTo(HashSet()) { acceptableEvent(it) }

    fun acceptableEvent(it: Note): Boolean =
        it.author == user &&
            (isNewThreadPost(it) || isGameEnding(it)) &&
            account.isAcceptable(it)

    /**
     * The move or resignation with which the user ended a chess game. It is a reply in its game's
     * thread, so it would fail [isNewThreadPost], but on a profile it stands for the whole game.
     */
    private fun isGameEnding(it: Note): Boolean = (it.event as? JesterEvent)?.ending() != null

    private fun isNewThreadPost(it: Note): Boolean =
        (
            it.event is TextNoteEvent ||
                it.event is CommentEvent ||
                it.event is ClassifiedsEvent ||
                it.event is FundraiserEvent ||
                it.event is EbookEvent ||
                it.event is MagazineEvent ||
                it.event is MagazineIssueEvent ||
                it.event is BirdexEvent ||
                it.event is BirdDetectionEvent ||
                it.event.isRenderableRepost() ||
                it.event is LongFormContentEvent ||
                it.event is WikiArticleEvent ||
                it.event is NipTextEvent ||
                it.event is ZapPollEvent ||
                it.event is PollEvent ||
                it.event is HighlightEvent ||
                it.event is InteractiveStoryPrologueEvent ||
                (it.event as? AudioTrackEvent)?.hasPlayableAudio() == true ||
                it.event is MusicTrackEvent ||
                it.event is MusicPlaylistEvent ||
                it.event is PodcastEpisodeEvent ||
                it.event is PodcastMetadataEvent ||
                it.event is AudioHeaderEvent ||
                it.event is VoiceEvent ||
                it.event is TorrentEvent ||
                it.event is AttestationEvent ||
                it.event is AttestationRequestEvent ||
                it.event is AttestorRecommendationEvent ||
                it.event is AttestorProficiencyEvent ||
                it.event is PublicationIndexEvent ||
                // Mirrors the Home filter: a rating with nothing to point at cannot be
                // rendered, so it would be an empty row on the author's own profile.
                (it.event as? EntityRatingEvent)?.hasTarget() == true
        ) &&
            it.isNewThread()

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()

    override fun limit() = 200
}
