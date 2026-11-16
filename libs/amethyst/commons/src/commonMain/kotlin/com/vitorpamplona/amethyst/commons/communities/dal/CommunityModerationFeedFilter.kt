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
package com.vitorpamplona.amethyst.commons.communities.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip72Communities.latestCommunityRules
import com.vitorpamplona.amethyst.commons.model.nip72Communities.violatesCommunityRules
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator

class CommunityModerationFeedFilter(
    val communityDefNote: AddressableNote,
    val account: Account,
    /** See [CommunityFeedFilter.hideRulesViolations]. */
    val hideRulesViolations: Boolean = false,
) : AdditiveFeedFilter<Note>() {
    val approvedFilter = CommunityFeedFilter(communityDefNote, account, hideRulesViolations)

    val communityDefEvent = communityDefNote.event as? CommunityDefinitionEvent
    val moderators = communityDefEvent?.moderatorKeys()?.toSet() ?: emptySet()

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + communityDefNote.idHex

    /** See [CommunityFeedFilter.rulesValidator]. */
    private fun rulesValidator(): CommunityRulesValidator? {
        if (!hideRulesViolations) return null
        val def = communityDefEvent ?: return null
        return latestCommunityRules(def)?.let { CommunityRulesValidator(it) }
    }

    override fun feed(): List<Note> {
        if (communityDefEvent == null) return emptyList()

        val validator = rulesValidator()
        val result =
            LocalCache.notes.mapFlattenIntoSet { _, it ->
                filterMap(it, validator)
            }

        return sort(result)
    }

    override fun updateListWith(
        oldList: List<Note>,
        newItems: Set<Note>,
    ): List<Note> {
        val approved = approvedFilter.applyFilter(newItems)

        val newOldList =
            if (approved.isNotEmpty()) {
                oldList - approved
            } else {
                oldList
            }

        val newItemsToBeAdded = applyFilter(newItems)
        return if (newItemsToBeAdded.isNotEmpty()) {
            val newList = newOldList.toSet() + newItemsToBeAdded
            sort(newList).take(limit())
        } else {
            newOldList
        }
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        if (communityDefEvent == null) return emptySet()

        val validator = rulesValidator()
        return collection
            .mapNotNull {
                filterMap(it, validator)
            }.flatten()
            .toSet()
    }

    private fun wasApprovedByCommunity(note: Note): Boolean =
        note.boosts.any {
            val approvalEvent = it.event
            (approvalEvent is CommunityPostApprovalEvent || approvalEvent is RepostEvent || approvalEvent is GenericRepostEvent) &&
                approvalEvent.pubKey in moderators &&
                approvalEvent.isForCommunity(this.communityDefNote.idHex)
        }

    private fun filterMap(
        note: Note,
        validator: CommunityRulesValidator?,
    ): List<Note>? {
        val noteEvent = note.event ?: return null

        return if (
            (noteEvent is TextNoteEvent || noteEvent is CommentEvent || noteEvent is CommunityPostApprovalEvent) &&
            noteEvent.isForCommunity(this.communityDefNote.idHex) &&
            noteEvent.pubKey !in moderators
        ) {
            if (noteEvent is CommunityPostApprovalEvent) {
                note.replyTo?.filter {
                    it.isNewThread() && !wasApprovedByCommunity(it) && passesRules(it, validator)
                }
            } else {
                if (note.isNewThread() && !wasApprovedByCommunity(note) && passesRules(note, validator)) {
                    listOf(note)
                } else {
                    null
                }
            }
        } else {
            null
        }
    }

    private fun passesRules(
        candidate: Note,
        validator: CommunityRulesValidator?,
    ): Boolean = validator == null || !violatesCommunityRules(validator, candidate)

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
