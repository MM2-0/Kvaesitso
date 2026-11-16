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
package com.vitorpamplona.amethyst.commons.geocaches.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.geocaches.dal.GeocachesFeedFilter
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.relayClient.geocaches.GeocacheListingKinds
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/**
 * The live geocaches whose geohash ladder overlaps [geohash].
 *
 * Deliberately narrower and wider than [GeocachesFeedFilter] at once: narrower because it only
 * admits caches in this cell, wider because it applies no top-nav follow list. A geohash screen
 * answers "what is *here*", and a cache hidden by a stranger is still here — filtering it out
 * would make the chip claim a place is empty when it is not.
 */
class GeocachesHereFeedFilter(
    val geohash: String,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-here-" + geohash

    override fun feed(): List<Note> = sort(LocalCache.addressables.filterIntoSet(GeocacheListingKinds) { _, it -> acceptable(it) })

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptable(it) }

    private fun acceptable(note: Note): Boolean {
        val event = note.event

        return event is GeocacheListingEvent &&
            event.isWellFormed() &&
            !event.isArchived() &&
            // A listing publishes its whole ladder from 3 to 9 characters, so a cell match is a
            // prefix match against any tagged level rather than equality.
            event.geohashes().any { it.startsWith(geohash) || geohash.startsWith(it) } &&
            !note.isHiddenFor(account.hiddenUsers.flow.value)
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex })
}
