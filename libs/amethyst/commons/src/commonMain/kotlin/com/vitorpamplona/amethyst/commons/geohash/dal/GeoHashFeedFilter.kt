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
package com.vitorpamplona.amethyst.commons.geohash.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.geohash.isTaggedGeoHash
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

class GeoHashFeedFilter(
    val tag: String,
    val relays: Set<NormalizedRelayUrl>,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + tag

    override fun feed(): List<Note> {
        val notes =
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, tag)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> = collection.filterTo(HashSet()) { acceptableEvent(it, tag) }

    fun acceptableEvent(
        it: Note,
        geoTag: String,
    ): Boolean =
        (acceptableViaHashtag(it.event, geoTag) || acceptableViaScope(it.event, geoTag)) &&
            !it.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(it)

    fun acceptableViaHashtag(
        event: Event?,
        geohash: String,
    ): Boolean =
        (
            event is TextNoteEvent ||
                event is LongFormContentEvent ||
                event is WikiArticleEvent ||
                event is ChannelMessageEvent ||
                event is EncryptedDmEvent ||
                event is ZapPollEvent ||
                event is AudioHeaderEvent ||
                event is MusicTrackEvent ||
                event is MusicPlaylistEvent ||
                event is PodcastEpisodeEvent ||
                event is PodcastMetadataEvent ||
                event is RoadEventReportEvent ||
                event is RoadEventConfirmationEvent
        ) &&
            event.isTaggedGeoHash(geohash)

    fun acceptableViaScope(
        event: Event?,
        geohash: String,
    ): Boolean = event is CommentEvent && event.isTaggedScope(geohash, GeohashId::match)

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
