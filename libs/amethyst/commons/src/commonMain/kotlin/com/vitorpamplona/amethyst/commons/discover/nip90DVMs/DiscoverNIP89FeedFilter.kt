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
package com.vitorpamplona.amethyst.commons.discover.nip90DVMs

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.hasFreshDvmHeartbeat
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverNIP89FeedFilter(
    val account: Account,
    val targetKind: Int = 5300,
) : AdditiveFeedFilter<Note>() {
    val lastAnnounced = TimeUtils.oneYearAgo()

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 50

    open fun followList(): TopFilter = account.settings.defaultDiscoveryFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val notes =
            LocalCache.addressables.filterIntoSet(AppDefinitionEvent.KIND) { _, it ->
                acceptApp(it)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.liveDiscoveryFollowLists.value,
            account.hiddenUsers.flow.value,
        )

    fun acceptApp(note: Note): Boolean {
        val noteEvent = note.event
        return if (noteEvent is AppDefinitionEvent) {
            acceptApp(noteEvent, note.relays)
        } else {
            false
        }
    }

    open fun acceptApp(
        noteEvent: AppDefinitionEvent,
        relays: List<NormalizedRelayUrl>,
    ): Boolean {
        val filterParams = buildFilterParams(account)
        return noteEvent.appMetaData()?.subscription != true &&
            filterParams.match(noteEvent, relays) &&
            noteEvent.includeKind(targetKind) &&
            noteEvent.createdAt > lastAnnounced &&
            LocalCache.hasFreshDvmHeartbeat(noteEvent)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) {
            acceptApp(it)
        }

    override fun sort(items: Set<Note>): List<Note> {
        val topFilter = account.liveDiscoveryFollowLists.value
        val discoveryTopFilterAuthors =
            when (topFilter) {
                is AuthorsByOutboxTopNavFilter -> topFilter.authors
                is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                is SingleCommunityTopNavFilter -> topFilter.authors
                is AuthorsByProxyTopNavFilter -> topFilter.authors
                is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                is AllFollowsByProxyTopNavFilter -> topFilter.authors
                else -> null
            }

        val followingKeySet =
            discoveryTopFilterAuthors ?: account.kind3FollowList.flow.value.authors

        val counter = ParticipantListBuilder()
        val participantCounts =
            items.associateWith { counter.countFollowsThatParticipateOn(it, followingKeySet) }

        val createdNote =
            items.associateWith { note ->
                ((note.event?.createdAt ?: 0) / 86400).toInt()
            }

        val feedOrder: Comparator<Note> =
            compareByDescending<Note> {
                participantCounts[it]
            }.thenByDescending {
                createdNote[it]
            }.thenBy { it.idHex }

        return items.sortedWith(feedOrder)
    }
}
