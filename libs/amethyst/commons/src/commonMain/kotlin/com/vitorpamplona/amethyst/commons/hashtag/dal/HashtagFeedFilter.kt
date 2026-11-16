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
package com.vitorpamplona.amethyst.commons.hashtag.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.isRenderableRepost
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

class HashtagFeedFilter(
    val tag: String,
    val relays: Set<NormalizedRelayUrl>,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + tag

    override fun feed(): List<Note> {
        val follows = account.followingKeySet()
        val notes =
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, tag, follows)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val follows = account.followingKeySet()
        return collection.filterTo(HashSet()) { acceptableEvent(it, tag, follows) }
    }

    fun acceptableEvent(
        it: Note,
        hashTag: String,
        follows: Set<HexKey>,
    ): Boolean =
        (
            acceptableViaHashtag(it.event, hashTag) ||
                acceptableViaScope(it.event, hashTag) ||
                acceptableViaFollowLabel(it, hashTag, follows)
        ) &&
            !it.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(it)

    /**
     * NIP-32: accept a post that a followed user has tagged with this hashtag via a kind
     * 1985 label event (namespace `#t`), even if the post's author never used the hashtag.
     * Requires the target to actually have an event so it can render.
     */
    fun acceptableViaFollowLabel(
        note: Note,
        hashTag: String,
        follows: Set<HexKey>,
    ): Boolean {
        if (note.event == null) return false
        val labelers = note.labels[hashTag.lowercase()] ?: return false
        return labelers.any { it.author?.pubkeyHex in follows }
    }

    fun acceptableViaHashtag(
        event: Event?,
        hashTag: String,
    ): Boolean =
        (
            event is TextNoteEvent ||
                event.isRenderableRepost() ||
                event is LongFormContentEvent ||
                event is WikiArticleEvent ||
                event is ChannelMessageEvent ||
                event is EncryptedDmEvent ||
                event is ZapPollEvent ||
                event is AudioHeaderEvent ||
                event is MusicTrackEvent ||
                event is MusicPlaylistEvent ||
                event is PodcastEpisodeEvent ||
                event is PodcastMetadataEvent
        ) &&
            event.isTaggedHash(hashTag)

    fun acceptableViaScope(
        event: Event?,
        hashTag: String,
    ): Boolean = event is CommentEvent && event.isTaggedScope(hashTag, HashtagId::match)

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
