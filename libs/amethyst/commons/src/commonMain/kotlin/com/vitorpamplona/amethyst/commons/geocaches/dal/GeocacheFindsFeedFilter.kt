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
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent

/**
 * The signed-in user's own found logs (kind 7516), newest first.
 *
 * No top-nav follow filter: this is the reader's own history, and hiding a find because the
 * cache's owner is outside the current follow list would be nonsense. Found logs are regular
 * events, so this scans `LocalCache.notes` rather than the addressable cache.
 */
class GeocacheFindsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-geocache-finds"

    override fun limit() = 500

    private fun mine() = account.userProfile().pubkeyHex

    override fun feed(): List<Note> {
        val me = mine()
        return sort(
            LocalCache.notes.filterIntoSet { _, it ->
                val noteEvent = it.event
                noteEvent is GeocacheFoundLogEvent && noteEvent.pubKey == me
            },
        )
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val me = mine()
        return newItems.filterTo(HashSet()) {
            val noteEvent = it.event
            noteEvent is GeocacheFoundLogEvent && noteEvent.pubKey == me
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex })
}
