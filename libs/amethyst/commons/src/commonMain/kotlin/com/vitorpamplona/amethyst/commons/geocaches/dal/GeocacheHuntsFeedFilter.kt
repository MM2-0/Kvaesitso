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
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent

/**
 * Curated hunts (kind 37517) the reader's top-nav filter admits.
 *
 * `isWellFormed` is enforced here because a curation list with no caches in it is not a hunt —
 * it is an empty page with a title, and the Hunts tab is a list of things to go and do.
 */
class GeocacheHuntsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 200

    fun followList(): TopFilter = account.settings.defaultGeocachesFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    override fun showHiddenKey(): Boolean = followList().isMuteList() || followList().isBlockList()

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        return sort(
            LocalCache.addressables.filterIntoSet(GeocacheCurationListEvent.KIND) { _, it ->
                val noteEvent = it.event
                noteEvent is GeocacheCurationListEvent && noteEvent.isWellFormed() && params.match(noteEvent, it.relays)
            },
        )
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return newItems.filterTo(HashSet()) {
            val noteEvent = it.event
            noteEvent is GeocacheCurationListEvent && noteEvent.isWellFormed() && params.match(noteEvent, it.relays)
        }
    }

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.liveGeocachesFollowLists.value,
            account.hiddenUsers.flow.value,
        )

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex })
}
