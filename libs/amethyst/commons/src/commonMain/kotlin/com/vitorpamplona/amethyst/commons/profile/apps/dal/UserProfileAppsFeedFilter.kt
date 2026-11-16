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
package com.vitorpamplona.amethyst.commons.profile.apps.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent

/**
 * The user's published nsites (NIP-5A, kinds 15128/35128) and napplets (NIP-5D, kinds
 * 15129/35129). These are all replaceable/addressable manifests, so they live in
 * `LocalCache.addressables`. The feed only lists them; tapping a card launches the sandboxed
 * `:napplet` process — execution never happens in the feed.
 */
class UserProfileAppsFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = user.pubkeyHex + "-ProfileApps"

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val notes =
            LocalCache.addressables
                .filter(APP_KINDS, user.pubkeyHex) { _, it ->
                    acceptableEvent(it, params)
                }

        return sort(notes.toSet()).toList()
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val params = buildFilterParams(account)
        return newItems.filterTo(HashSet()) { acceptableEvent(it, params) }
    }

    private fun acceptableEvent(
        it: Note,
        params: FilterByListParams,
    ): Boolean {
        val noteEvent = it.event
        // A kind 15129/35129 that isn't a valid NIP-5D manifest is another app's format, not a napplet.
        val isOwnManifest =
            noteEvent?.pubKey == user.pubkeyHex &&
                (noteEvent is RootSiteEvent || noteEvent is NamedSiteEvent || ((noteEvent is RootNappletEvent || noteEvent is NamedNappletEvent) && noteEvent.isValid()))

        return isOwnManifest && params.match(noteEvent, it.relays) && account.isAcceptable(it)
    }

    private fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveStoriesFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()

    companion object {
        val APP_KINDS = listOf(RootSiteEvent.KIND, NamedSiteEvent.KIND, RootNappletEvent.KIND, NamedNappletEvent.KIND)
    }
}
