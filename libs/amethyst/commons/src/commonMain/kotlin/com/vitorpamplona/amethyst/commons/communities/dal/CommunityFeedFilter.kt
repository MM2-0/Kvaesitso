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
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator

class CommunityFeedFilter(
    val communityDefNote: AddressableNote,
    val account: Account,
    /**
     * When true, candidate events are additionally checked against the latest
     * NIP-9B `kind:34551` rules document for this community and dropped on any
     * violation. No-op when no rules document is cached. Default false preserves
     * pre-9A behaviour for any caller that constructs the filter directly.
     */
    val hideRulesViolations: Boolean = false,
) : AdditiveFeedFilter<Note>() {
    val communityDefEvent = communityDefNote.event as? CommunityDefinitionEvent
    val moderators = communityDefEvent?.moderatorKeys()?.toSet() ?: emptySet()

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + communityDefNote.idHex

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    /**
     * Memoised per-feed validator. Recomputed lazily on each `feed()` /
     * `applyFilter()` call; the latest cached rules event for the community
     * may change between calls when the rules sub-assembler delivers an update.
     * Null when [hideRulesViolations] is off, when no rules are cached, or when
     * the community definition itself isn't available.
     */
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

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        if (communityDefEvent == null) return emptySet()

        val validator = rulesValidator()
        return collection
            .mapNotNull {
                filterMap(it, validator)
            }.flatten()
            .toSet()
    }

    private fun filterMap(
        note: Note,
        validator: CommunityRulesValidator?,
    ): List<Note>? {
        val noteEvent = note.event ?: return null

        return if (
            // Only Approvals
            (noteEvent is TextNoteEvent || noteEvent is CommentEvent || noteEvent is CommunityPostApprovalEvent) &&
            noteEvent.pubKey in moderators &&
            noteEvent.isForCommunity(this.communityDefNote.idHex)
        ) {
            if (noteEvent is CommunityPostApprovalEvent) {
                note.replyTo?.filter {
                    it.isNewThread() && passesRules(it, validator)
                }
            } else {
                if (note.isNewThread() && passesRules(note, validator)) {
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
