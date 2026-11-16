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
package com.vitorpamplona.amethyst.commons.discover.nip72Communities

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.mapNotNullIntoSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

open class DiscoverCommunityFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 150

    open fun followList(): TopFilter = account.settings.defaultDiscoveryFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        // Here we only need to look for CommunityDefinition Events
        val notes =
            LocalCache.addressables.mapNotNullIntoSet(CommunityDefinitionEvent.KIND) { key, note ->
                val noteEvent = note.event
                if (noteEvent == null && shouldInclude(key, filterParams, note.relays)) {
                    // send unloaded communities to the screen
                    note
                } else if (noteEvent is CommunityDefinitionEvent && filterParams.match(noteEvent, note.relays)) {
                    note
                } else {
                    null
                }
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        // here, we need to look for CommunityDefinition in new collection AND new CommunityDefinition from Post Approvals
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        return collection
            .mapNotNull { note ->
                // note event here will never be null
                val noteEvent = note.event
                if (noteEvent is CommunityDefinitionEvent && filterParams.match(noteEvent, note.relays)) {
                    listOf(note)
                } else if (noteEvent is CommunityPostApprovalEvent) {
                    noteEvent.communityAddresses().mapNotNull {
                        val definitionNote = LocalCache.getOrCreateAddressableNote(it)
                        val definitionEvent = definitionNote.event

                        if (definitionEvent == null && shouldInclude(it, filterParams, definitionNote.relays)) {
                            definitionNote
                        } else if (definitionEvent is CommunityDefinitionEvent && filterParams.match(definitionEvent, definitionNote.relays)) {
                            definitionNote
                        } else {
                            null
                        }
                    }
                } else {
                    null
                }
            }.flatten()
            .toSet()
    }

    private fun shouldInclude(
        aTag: Address?,
        params: FilterByListParams,
        comingFrom: List<NormalizedRelayUrl> = emptyList(),
    ) = aTag != null && aTag.kind == CommunityDefinitionEvent.KIND && params.match(aTag, comingFrom)

    override fun sort(items: Set<Note>): List<Note> {
        val lastNotesCreatedAt =
            items.associateWith { note ->
                val boosts = note.boosts
                var max = 0L
                for (boost in boosts) {
                    val createdAt = boost.createdAt()
                    if (createdAt != null && createdAt > max) {
                        max = createdAt
                    }
                }
                max
            }

        val createdNote =
            items.associateWith { note ->
                note.createdAt() ?: 0L
            }

        val comparator: Comparator<Note> =
            compareByDescending<Note> {
                lastNotesCreatedAt[it]
            }.thenByDescending {
                createdNote[it]
            }.thenBy {
                it.idHex
            }

        return items.sortedWith(comparator)
    }
}
