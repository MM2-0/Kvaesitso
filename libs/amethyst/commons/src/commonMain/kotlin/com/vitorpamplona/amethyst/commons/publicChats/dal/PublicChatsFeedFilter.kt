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
package com.vitorpamplona.amethyst.commons.publicChats.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel

class PublicChatsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 200

    fun followList(): TopFilter = account.settings.defaultPublicChatsFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val allChannelNotes =
            LocalCache.publicChatChannels.mapNotNullIntoSet { _, channel ->
                val note = LocalCache.getNoteIfExists(channel.idHex)
                val noteEvent = note?.event

                if (noteEvent == null || params.match(noteEvent, note.relays)) {
                    note
                } else {
                    null
                }
            }

        return sort(allChannelNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.livePublicChatsFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.mapNotNullTo(HashSet()) { note ->
            val noteEvent = note.event
            if (noteEvent is ChannelCreateEvent && params.match(noteEvent, note.relays)) {
                if ((LocalCache.getPublicChatChannelIfExists(noteEvent.id)?.notes?.size() ?: 0) > 0) {
                    note
                } else {
                    null
                }
            } else if (noteEvent is IsInPublicChatChannel) {
                val channel = noteEvent.channelId()?.let { LocalCache.checkGetOrCreateNote(it) }
                val channelEvent = channel?.event

                if (channel != null &&
                    (channelEvent == null || (channelEvent is ChannelCreateEvent && params.match(channelEvent, note.relays)))
                ) {
                    if ((LocalCache.getPublicChatChannelIfExists(channel.idHex)?.notes?.size() ?: 0) > 0) {
                        channel
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    override fun sort(items: Set<Note>): List<Note> {
        val lastNote =
            items.associateWith { note ->
                LocalCache.getPublicChatChannelIfExists(note.idHex)?.lastNote?.createdAt() ?: 0L
            }

        val createdNote =
            items.associateWith { note ->
                note.createdAt() ?: 0L
            }

        val comparator: Comparator<Note> =
            compareByDescending<Note> {
                lastNote[it]
            }.thenByDescending {
                createdNote[it]
            }.thenBy {
                it.idHex
            }

        return items.sortedWith(comparator)
    }
}
