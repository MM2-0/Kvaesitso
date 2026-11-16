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
package com.vitorpamplona.amethyst.commons.livestreams.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.service.OnlineStatusCache
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag

class LiveStreamsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 50

    fun followList(): TopFilter = account.settings.defaultLiveStreamsFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val allChannelNotes = LocalCache.liveChatChannels.mapNotNull { _, channel -> LocalCache.getAddressableNoteIfExists(channel.address) }
        val allMessageNotes = LocalCache.liveChatChannels.map { _, channel -> channel.notes.filter { _, it -> it.event is LiveActivitiesEvent } }.flatten()

        val notes = innerApplyFilter(allChannelNotes + allMessageNotes)

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveLiveStreamsFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event
            (noteEvent is LiveActivitiesEvent || noteEvent is MeetingSpaceEvent || noteEvent is MeetingRoomEvent) &&
                filterParams.match(noteEvent, it.relays)
        }
    }

    override fun sort(items: Set<Note>): List<Note> {
        val topFilter = account.liveLiveStreamsFollowLists.value
        val liveStreamsTopFilterAuthors =
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
            liveStreamsTopFilterAuthors ?: account.kind3FollowList.flow.value.authors

        val counter = ParticipantListBuilder()
        val participantCounts =
            items.associate { it to counter.countFollowsThatParticipateOn(it, followingKeySet) }

        val allParticipants =
            items.associate { it to counter.countFollowsThatParticipateOn(it, null) }

        // Snapshots the status order once per item. convertStatusToOrder reads the
        // OnlineStatusCache and the moving five-minute window, both of which can change
        // mid-sort (a background online check can update the cache). Reading it lazily inside
        // the comparator makes the ordering unstable and TimSort throws
        // "Comparison method violates its general contract!".
        val statusOrders = items.associateWith { convertStatusToOrder(it.event) }

        return items
            .sortedWith(
                compareBy(
                    { statusOrders[it] },
                    { participantCounts[it] },
                    { allParticipants[it] },
                    {
                        when (val e = it.event) {
                            is LiveActivitiesEvent -> e.starts() ?: it.createdAt()
                            is MeetingRoomEvent -> e.starts() ?: it.createdAt()
                            else -> it.createdAt()
                        }
                    },
                    { it.idHex },
                ),
            ).reversed()
    }

    fun convertStatusToOrder(event: com.vitorpamplona.quartz.nip01Core.core.Event?): Int {
        if (event == null) return 0
        return when (event) {
            is LiveActivitiesEvent -> {
                val url = event.streaming() ?: return 0
                when (event.status()) {
                    StatusTag.STATUS.LIVE -> {
                        if (OnlineStatusCache.isCachedAndOffline(url)) 0 else 2
                    }

                    StatusTag.STATUS.PLANNED -> {
                        1
                    }

                    StatusTag.STATUS.ENDED -> {
                        0
                    }

                    else -> {
                        0
                    }
                }
            }

            is MeetingRoomEvent -> {
                when (event.status()) {
                    StatusTag.STATUS.LIVE -> 2
                    StatusTag.STATUS.PLANNED -> 1
                    StatusTag.STATUS.ENDED -> 0
                    else -> 0
                }
            }

            is MeetingSpaceEvent -> {
                when (event.status()) {
                    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.LIVE -> 2
                    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.PRIVATE -> 1
                    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.ENDED -> 0
                    else -> 0
                }
            }

            else -> {
                0
            }
        }
    }
}
