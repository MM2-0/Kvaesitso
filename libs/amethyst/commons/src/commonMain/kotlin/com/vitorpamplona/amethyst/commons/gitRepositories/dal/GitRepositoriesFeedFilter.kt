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
package com.vitorpamplona.amethyst.commons.gitRepositories.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

class GitRepositoriesFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 200

    fun followList(): TopFilter = account.settings.defaultGitRepositoriesFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)
        val notes =
            LocalCache.addressables.filterIntoSet(GitRepositoryEvent.KIND) { _, it ->
                val noteEvent = it.event
                noteEvent is GitRepositoryEvent && params.match(noteEvent, it.relays)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.liveGitRepositoriesFollowLists.value,
            account.hiddenUsers.flow.value,
        )

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event
            noteEvent is GitRepositoryEvent && params.match(noteEvent, it.relays)
        }
    }

    override fun sort(items: Set<Note>): List<Note> = oneCardPerRepository(items).sortedByDefaultFeedOrder()

    /**
     * Co-maintainers each publish an announcement under the same identifier; once they confirm each
     * other (GitRepositoryMembership) they are one repository, so it gets one card: the newest of
     * their announcements, whose name and description are the repository's current ones. A
     * same-identifier announcement nobody confirmed stays a card of its own.
     */
    private fun oneCardPerRepository(items: Set<Note>): Set<Note> {
        val authority = LocalCache.gitAuthority
        val result = HashSet<Note>()
        val byRepository = HashMap<String, Note>()
        for (note in items) {
            val event = note.event as? GitRepositoryEvent
            val maintainers = event?.let { authority.membership(Address(GitRepositoryEvent.KIND, it.pubKey, it.dTag()))?.confirmedMaintainers }
            if (event == null || maintainers == null || maintainers.size < 2 || event.pubKey !in maintainers) {
                result.add(note)
                continue
            }
            val key = event.dTag() + ":" + maintainers.sorted().joinToString(",")
            val kept = byRepository[key]
            if (kept == null || (note.createdAt() ?: 0) > (kept.createdAt() ?: 0)) byRepository[key] = note
        }
        result.addAll(byRepository.values)
        return result
    }
}
