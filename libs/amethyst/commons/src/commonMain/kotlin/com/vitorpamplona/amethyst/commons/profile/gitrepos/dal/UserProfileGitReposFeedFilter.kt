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
package com.vitorpamplona.amethyst.commons.profile.gitrepos.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * NIP-34 git repository announcements (kind:30617) authored by the viewed profile.
 * Only the profile owner's own announcements count: the addressable cache lookup is
 * keyed by (kind, author) and [acceptableEvent] re-checks the author on the apply path.
 */
class UserProfileGitReposFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + "ProfileGitRepos" + "-" + user.pubkeyHex

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val repos =
            LocalCache.addressables
                .filter(GitRepositoryEvent.KIND, user.pubkeyHex) { _, it ->
                    acceptableEvent(it, params)
                }

        return sort(repos.toSet()).toList()
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

        return noteEvent is GitRepositoryEvent &&
            noteEvent.pubKey == user.pubkeyHex &&
            params.match(noteEvent, it.relays) &&
            account.isAcceptable(it)
    }

    private fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveStoriesFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
