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
package com.vitorpamplona.amethyst.commons.calendars.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.upcomingFirstCalendarOrder
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Feed of NIP-52 calendar *appointments* — kinds 31922 (date-slot) and 31923 (time-slot). The
 * NIP calls kind 31924 a "calendar" (a list of appointments), so this filter intentionally does
 * not load 31924; see [CalendarCollectionsFeedFilter] for that.
 */
class CalendarAppointmentsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 500

    fun followList(): TopFilter = account.settings.defaultCalendarsFollowList.value

    private fun TopFilter.isMuteList() = this is TopFilter.MuteList

    private fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    private fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    /**
     * Reads the ADDRESSABLE notes, not `LocalCache.notes`.
     *
     * 31922/31923 are addressable, so [LocalCache.consumeBaseReplaceable] keeps two objects per
     * appointment: the canonical [com.vitorpamplona.amethyst.commons.model.AddressableNote] under
     * its address, and a throwaway version note under the event id. Only the canonical one is
     * handed to feeds when an event arrives, carries the relays it came from, and holds the latest
     * version; the version note has its references moved away on arrival, records no relays, is
     * weakly held, and is what [com.vitorpamplona.amethyst.model.CachePruner] sweeps on every app
     * switch.
     *
     * Scanning `notes` here meant the full rebuild and the live update disagreed about which object
     * represents an appointment: the same event arrived under two different [Note.idHex] values, so
     * it could sit in the feed twice, and each rebuild after a resume swapped one identity for the
     * other — the views flickered events in and out on every trip through the background. An edit
     * showed up as a second entry for the same appointment, too, since both versions matched.
     *
     * [CalendarCollectionsFeedFilter] and [com.vitorpamplona.amethyst.ui.dal.ArticlesFeedFilter]
     * already read addressables this way; this is the same query, narrowed to a kind range instead
     * of walking every note in the cache.
     */
    override fun feed(): List<Note> {
        val params = buildFilterParams(account)
        val notes =
            LocalCache.addressables.filterIntoSet(APPOINTMENT_KINDS) { _, it ->
                val e = it.event
                (e is CalendarTimeSlotEvent || e is CalendarDateSlotEvent) && params.match(e, it.relays)
            }
        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.liveCalendarsFollowLists.value,
            account.hiddenUsers.flow.value,
        )

    /**
     * The [AddressableNote] check keeps the additive path on the same objects [feed] returns.
     * `LocalCache` only ever hands feeds the canonical note for an addressable kind, so this
     * rejects nothing that arrives today; it is here so a version note can never slip back in and
     * put a second copy of an appointment next to the one already on screen.
     */
    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)
        return collection.filterTo(HashSet()) {
            val e = it.event
            it is AddressableNote && (e is CalendarTimeSlotEvent || e is CalendarDateSlotEvent) && params.match(e, it.relays)
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(upcomingFirstCalendarOrder(TimeUtils.now()))

    companion object {
        private val APPOINTMENT_KINDS = listOf(CalendarTimeSlotEvent.KIND, CalendarDateSlotEvent.KIND)
    }
}
