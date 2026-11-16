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
package com.vitorpamplona.amethyst.commons.communities.list.dal

import com.vitorpamplona.amethyst.commons.discover.nip72Communities.DiscoverCommunityFeedFilter
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

class CommunitiesFeedFilter(
    account: Account,
) : DiscoverCommunityFeedFilter(account) {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-communities-" + followList().code

    override fun followList(): TopFilter = account.settings.defaultCommunitiesFollowList.value

    override fun feed(): List<Note> {
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveCommunitiesFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        val notes =
            LocalCache.addressables.mapNotNullIntoSet(CommunityDefinitionEvent.KIND) { key, note ->
                val noteEvent = note.event
                if (noteEvent == null && shouldInclude(key, filterParams, note.relays)) {
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

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams =
            FilterByListParams.create(
                followLists = account.liveCommunitiesFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )

        return collection
            .mapNotNull { note ->
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
}
