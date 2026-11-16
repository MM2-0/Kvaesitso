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
package com.vitorpamplona.amethyst.commons.podcasts.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

/**
 * Everything a single podcast (authored by [podcastPubkey]) publishes for its show page: NIP-F4
 * episodes (kind 54, regular events in `LocalCache.notes`), Podcasting-2.0 episodes (kind 30054)
 * and Podcasting-2.0 trailers (kind 30055, both addressable events in `LocalCache.addressables`).
 * Episodes go through the shared [PodcastEpisode] interface; trailers are matched by their concrete
 * type and rendered distinctly. In every model the show's pubkey authors its own content.
 * Most-recent-first via [sortedByDefaultFeedOrder].
 */
class OnePodcastEpisodesFeedFilter(
    val podcastPubkey: HexKey,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "podcast-" + podcastPubkey

    override fun feed(): List<Note> {
        val regular =
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it)
            }
        val episodes =
            cache.addressables.filterIntoSet(Podcasting20EpisodeEvent.KIND) { _, it ->
                acceptableEvent(it)
            }
        val trailers =
            cache.addressables.filterIntoSet(Podcasting20TrailerEvent.KIND) { _, it ->
                acceptableEvent(it)
            }
        return sort(regular + episodes + trailers)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptableEvent(it) }

    private fun acceptableEvent(note: Note): Boolean {
        val noteEvent = note.event ?: return false
        val isShowContent = noteEvent is PodcastEpisode || noteEvent is Podcasting20TrailerEvent
        return isShowContent &&
            noteEvent.pubKey == podcastPubkey &&
            !note.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(note)
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
