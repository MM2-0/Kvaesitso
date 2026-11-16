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
package com.vitorpamplona.amethyst.commons.followPacks.feed.dal

import com.vitorpamplona.amethyst.commons.audio.hasPlayableAudio
import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.isRenderableRepost
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsByProxyTopNavFilter
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
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
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

class FollowPackFeedNewThreadFeedFilter(
    val followPackNote: AddressableNote,
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
                NipTextEvent.KIND,
                ClassifiedsEvent.KIND,
                FundraiserEvent.KIND,
                EbookEvent.KIND,
                MagazineEvent.KIND,
                MagazineIssueEvent.KIND,
                BirdexEvent.KIND,
                LongFormContentEvent.KIND,
            )
    }

    val followPackEvent = followPackNote.event as? StarterPackEvent
    val follows = followPackEvent?.followIdSet() ?: emptySet()

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followPackNote.idHex

    override fun showHiddenKey(): Boolean = false

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists =
                if (account.proxyRelayList.flow.value
                        .isEmpty()
                ) {
                    AllUserFollowsByOutboxTopNavFilter(
                        authors = follows,
                        defaultRelays = account.defaultGlobalRelays.flow,
                        blockedRelays = account.blockedRelayList.flow,
                    )
                } else {
                    AllUserFollowsByProxyTopNavFilter(
                        authors = follows,
                        proxyRelays = account.proxyRelayList.flow.value,
                    )
                },
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, note ->
                // Avoids processing addressables twice.
                (note.event?.kind ?: 99999) < 10000 && acceptableEvent(note, filterParams)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet(
                kinds = ADDRESSABLE_KINDS,
            ) { _, note ->
                acceptableEvent(note, filterParams)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, filterParams)
        }
    }

    private fun acceptableEvent(
        it: Note,
        filterParams: FilterByListParams,
    ): Boolean {
        val noteEvent = it.event
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
                noteEvent is HighlightEvent ||
                noteEvent is InteractiveStoryPrologueEvent ||
                noteEvent is CommentEvent ||
                (noteEvent is AudioTrackEvent && noteEvent.hasPlayableAudio()) ||
                noteEvent is MusicTrackEvent ||
                noteEvent is MusicPlaylistEvent ||
                noteEvent is PodcastEpisodeEvent ||
                noteEvent is PodcastMetadataEvent ||
                noteEvent is VoiceEvent ||
                noteEvent is AudioHeaderEvent
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
