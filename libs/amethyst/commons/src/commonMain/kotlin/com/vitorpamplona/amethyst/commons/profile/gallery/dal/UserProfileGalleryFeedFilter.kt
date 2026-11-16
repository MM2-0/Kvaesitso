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
package com.vitorpamplona.amethyst.commons.profile.gallery.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableVideoEvent
import com.vitorpamplona.quartz.nip71Video.RegularVideoEvent

class UserProfileGalleryFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + "ProfileGallery"

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, params, user)
            }

        val addressableNotes =
            LocalCache.addressables
                .filter(
                    // Both NIP-71 addressable kinds: acceptableEvent() takes any
                    // AddressableVideoEvent, so listing 34236 twice silently kept every
                    // 34235 (horizontal) video out of the gallery.
                    listOf(AddressableShortVideoEvent.KIND, AddressableNormalVideoEvent.KIND),
                    user.pubkeyHex,
                ) { _, it ->
                    acceptableEvent(it, params, user)
                }

        return sort(addressableNotes + notes).toList()
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, params, user) }
    }

    fun acceptableEvent(
        it: Note,
        params: FilterByListParams,
        user: User,
    ): Boolean {
        val noteEvent = it.event
        val authoredByUser =
            it.event?.pubKey == user.pubkeyHex &&
                (
                    noteEvent is PictureEvent ||
                        noteEvent is RegularVideoEvent ||
                        (noteEvent is AddressableVideoEvent && it is AddressableNote) ||
                        (noteEvent is ProfileGalleryEntryEvent && noteEvent.hasUrl() && noteEvent.hasFromEvent())
                )

        // Clips are authored by viewers, not the host — accept them when they reference
        // a stream hosted by this user AND carry a playable URL.
        val clipOfUsersStream =
            noteEvent is LiveActivitiesClipEvent &&
                noteEvent.host() == user.pubkeyHex &&
                !noteEvent.videoUrl().isNullOrBlank()

        return (authoredByUser || clipOfUsersStream) &&
            params.match(noteEvent, it.relays) &&
            account.isAcceptable(it)
    }

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveStoriesFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
