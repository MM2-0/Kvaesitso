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
package com.vitorpamplona.amethyst.commons.model.composer

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Selection rules for the composer's audience picker, kept free of Compose and
 * of the Account so they can be unit tested on the JVM.
 *
 * The picker never mutates anything itself: it produces a set of pubkeys the
 * screen hands to [com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.ShortNotePostViewModel.addAllToReplyList].
 */
enum class AudienceListKind {
    /** NIP-51 kind 30000 people list. Can carry encrypted (private) members. */
    PEOPLE_LIST,

    /** kind 39089 follow pack. Public members only. */
    FOLLOW_PACK,
}

@Immutable
data class AudienceList(
    val id: String,
    val kind: AudienceListKind,
    val title: String,
    val publicMembers: ImmutableList<User> = persistentListOf(),
    val privateMembers: ImmutableList<User> = persistentListOf(),
) {
    val memberCount: Int = publicMembers.size + privateMembers.size

    fun members(): List<User> = publicMembers + privateMembers

    fun matches(query: String): Boolean = query.isBlank() || title.contains(query.trim(), ignoreCase = true)
}

/**
 * One reviewable row in the picker. Every flag is a reason the row is treated
 * differently from an ordinary public member — see [defaultSelection].
 */
@Immutable
data class AudienceMember(
    val user: User,
    /**
     * Encrypted member of a kind-30000 list. Adding one publishes their pubkey
     * in the note's `p` tags, which every other recipient can read — so these
     * never start selected.
     */
    val isPrivateMember: Boolean = false,
    /** Already in the composer's audience; shown for a truthful count, not re-added. */
    val isAlreadyInAudience: Boolean = false,
    /** Muted or marked as a spammer by this account. */
    val isHidden: Boolean = false,
) {
    val pubkeyHex: HexKey get() = user.pubkeyHex
}

/** How the audience size that a pending add would produce compares to the caps. */
sealed interface AudienceCap {
    data object Fine : AudienceCap

    /** Allowed, but the cost is disclosed before it happens. */
    data class OverSoft(
        val total: Int,
    ) : AudienceCap

    /** Refused: the add would produce an audience we won't fan out to. */
    data class OverHard(
        val total: Int,
    ) : AudienceCap
}

object AudienceSelection {
    /**
     * Above this many recipients the picker discloses what the send will cost.
     * A private note builds one seal + one wrap per recipient, and on a NIP-46
     * bunker or a NIP-55 external signer that is two round trips each.
     */
    const val SOFT_CAP = 25

    /** Above this many the add is refused rather than silently truncated. */
    const val HARD_CAP = 100

    /** How many faces the resting facepile shows before collapsing into "+N". */
    const val PILE_FACES = 3

    /** How many names the resting summary spells out before "& N others". */
    const val SUMMARY_NAMES = 2

    fun buildMembers(
        list: AudienceList,
        alreadyInAudience: Set<HexKey>,
        hiddenUsers: Set<HexKey>,
    ): List<AudienceMember> {
        val publicIds = list.publicMembers.mapTo(mutableSetOf()) { it.pubkeyHex }
        val privateIds = list.privateMembers.mapTo(mutableSetOf()) { it.pubkeyHex }

        return list.members().distinctBy { it.pubkeyHex }.map { user ->
            AudienceMember(
                user = user,
                // Only members that appear *solely* in the encrypted half are a
                // disclosure risk. Someone listed in both halves is already
                // public, so warning about them would be noise that trains the
                // user to ignore the badge that matters.
                isPrivateMember = user.pubkeyHex in privateIds && user.pubkeyHex !in publicIds,
                isAlreadyInAudience = user.pubkeyHex in alreadyInAudience,
                isHidden = user.pubkeyHex in hiddenUsers,
            )
        }
    }

    /**
     * What the review step starts with: every ordinary member, plus the ones
     * already in the audience so the header count tells the truth. Private
     * members and muted people need a deliberate tap.
     *
     * A list that would blow the hard cap opens with nothing new selected
     * instead. Selecting all of it would land the review in a state the confirm
     * button refuses, leaving the only way out a hundred-odd individual taps.
     */
    fun defaultSelection(
        members: List<AudienceMember>,
        currentAudienceSize: Int = 0,
    ): Set<HexKey> {
        val alreadyIn = members.filter { it.isAlreadyInAudience }.mapTo(mutableSetOf()) { it.pubkeyHex }
        val proposed = members.filter { !it.isAlreadyInAudience && !it.isPrivateMember && !it.isHidden }

        if (capFor(currentAudienceSize, proposed.size) is AudienceCap.OverHard) return alreadyIn

        return proposed.mapTo(alreadyIn) { it.pubkeyHex }
    }

    /** The pubkeys a confirm would actually add — the selection minus what is already there. */
    fun pendingAdditions(
        members: List<AudienceMember>,
        selected: Set<HexKey>,
    ): List<User> =
        members
            .filter { it.pubkeyHex in selected && !it.isAlreadyInAudience }
            .map { it.user }

    fun capFor(
        currentAudienceSize: Int,
        additions: Int,
    ): AudienceCap {
        val total = currentAudienceSize + additions
        return when {
            total > HARD_CAP -> AudienceCap.OverHard(total)
            total > SOFT_CAP -> AudienceCap.OverSoft(total)
            else -> AudienceCap.Fine
        }
    }

    /**
     * Works out what a bulk add changes: who is genuinely new, and what the
     * provenance map becomes.
     *
     * Provenance is recorded for the newcomers only. Recording it for everyone
     * in the list would let the group chip's undo evict somebody who was in the
     * audience for an unrelated reason — dropping the author of the note being
     * replied to, say, just because a list happened to contain them.
     */
    fun addToAudience(
        current: List<User>,
        incoming: Collection<User>,
        provenance: Map<HexKey, Set<String>>,
        fromListTag: String?,
        currentlyMuted: Set<HexKey> = emptySet(),
    ): AudienceAddition {
        // Snapshot before filtering: the dedup below adds to its own set, so
        // testing membership against that set afterwards would report every
        // incoming pubkey as already known.
        val existing = current.mapTo(mutableSetOf()) { it.pubkeyHex }

        val appended = mutableSetOf<HexKey>()
        val newcomers = incoming.filter { it.pubkeyHex !in existing && appended.add(it.pubkeyHex) }

        // A muted person is in pTags but not in the audience, so a list that
        // contains them genuinely changes the outcome by un-muting them. They
        // count as introduced even though they are not new to pTags — otherwise
        // undoing the list would leave them silently in a private note's
        // audience after every one of their batch-mates had been removed.
        val seen = mutableSetOf<HexKey>()
        val introduced =
            incoming.filter {
                val alreadyInEffectiveAudience = it.pubkeyHex in existing && it.pubkeyHex !in currentlyMuted
                !alreadyInEffectiveAudience && seen.add(it.pubkeyHex)
            }
        val unmutes = introduced.mapTo(mutableSetOf()) { it.pubkeyHex }

        if (fromListTag == null || introduced.isEmpty()) return AudienceAddition(newcomers, provenance, unmutes)

        val next = provenance.toMutableMap()
        introduced.forEach { user ->
            next[user.pubkeyHex] = (next[user.pubkeyHex] ?: emptySet()) + fromListTag
        }
        return AudienceAddition(newcomers, next, unmutes)
    }

    /** Members that can be bulk-toggled by "select all" — the already-added rows are locked on. */
    fun toggleableIds(members: List<AudienceMember>): Set<HexKey> = members.filterNot { it.isAlreadyInAudience }.mapTo(mutableSetOf()) { it.pubkeyHex }

    /**
     * Drops [users] from a provenance map and reports which of them no longer
     * belong to any list. Used by the group chip: removing "Close friends"
     * must not evict somebody who was also added by hand or by another list.
     */
    fun removeListFromProvenance(
        provenance: Map<HexKey, Set<String>>,
        listId: String,
    ): ProvenanceRemoval {
        val next = mutableMapOf<HexKey, Set<String>>()
        val orphaned = mutableSetOf<HexKey>()
        provenance.forEach { (pubkey, lists) ->
            if (listId in lists) {
                val remaining = lists - listId
                if (remaining.isEmpty()) {
                    orphaned.add(pubkey)
                } else {
                    next[pubkey] = remaining
                }
            } else {
                next[pubkey] = lists
            }
        }
        return ProvenanceRemoval(next, orphaned)
    }

    data class ProvenanceRemoval(
        val provenance: Map<HexKey, Set<String>>,
        /** Pubkeys whose only source was the removed list: safe to drop from the audience. */
        val orphaned: Set<HexKey>,
    )

    /** Lists fully represented in the current audience, newest membership wins for the chip label. */
    fun activeGroupChips(
        provenance: Map<HexKey, Set<String>>,
        audience: Set<HexKey>,
        lists: List<AudienceList>,
    ): List<AudienceGroupChip> {
        if (provenance.isEmpty()) return emptyList()
        val countsById = mutableMapOf<String, Int>()
        provenance.forEach { (pubkey, listIds) ->
            if (pubkey in audience) {
                listIds.forEach { id -> countsById[id] = (countsById[id] ?: 0) + 1 }
            }
        }
        return countsById.mapNotNull { (id, count) ->
            val title = lists.firstOrNull { it.id == id }?.title ?: return@mapNotNull null
            AudienceGroupChip(id, title, count)
        }
    }
}

@Immutable
data class AudienceAddition(
    /** People not yet in `pTags` at all — these get appended. */
    val newcomers: List<User>,
    val provenance: Map<HexKey, Set<String>>,
    /** People this add brings into the effective audience, so their bell comes back on. */
    val unmutes: Set<HexKey> = emptySet(),
)

@Immutable
data class AudienceGroupChip(
    val listId: String,
    val title: String,
    val count: Int,
)
