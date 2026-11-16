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
package com.vitorpamplona.amethyst.commons.ui.note.creators.notify

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.composer.AudienceSelection
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The audience a composer will `p` tag — and, when the note is sealed, the set
 * of people who can decrypt it at all.
 *
 * Composers name their own backing field (`pTags` on the short-note composer,
 * `notifying` on the comment composer), so this interface maps onto whichever
 * one they already have and supplies the shared behaviour on top. The rules it
 * delegates to live in [AudienceSelection], which is Compose-free and unit
 * tested; everything here is thin state plumbing.
 */
@Stable
interface IAudience {
    /** The people who will be p-tagged, muted ones included. */
    var audienceMembers: List<User>?

    /**
     * Members whose bell is off. They keep their chip — so they are one tap from
     * coming back — but are dropped from the outgoing event's `p` tags.
     */
    var mutedNotifies: Set<HexKey>

    /**
     * Which list each pubkey arrived from. Display and undo only; never read
     * when building the event, where [activeAudience] is the single source.
     */
    var notifyProvenance: Map<HexKey, Set<String>>

    /** Whether the manage sheet is open. */
    var wantsToManageAudience: Boolean

    /** Backs the manage sheet's search box. */
    val audienceSearchText: TextFieldState

    /** Called after every mutation so the composer can re-save its draft. */
    fun onAudienceChanged()

    /** Who will actually be p-tagged: the chip list minus the muted ones. */
    fun activeAudience(): List<User>? = audienceMembers?.filter { it.pubkeyHex !in mutedNotifies }

    fun toggleNotify(user: User) {
        mutedNotifies =
            if (user.pubkeyHex in mutedNotifies) {
                mutedNotifies - user.pubkeyHex
            } else {
                mutedNotifies + user.pubkeyHex
            }
        onAudienceChanged()
    }

    /**
     * Adds people in one shot, deliberately with a single write per field: N
     * individual adds would recompose the audience row N times and bump the
     * draft version N times for one user gesture.
     *
     * [fromListTag] records provenance so a whole bulk add can be undone as a
     * unit; pass null for people picked one at a time.
     */
    fun addAllToAudience(
        users: Collection<User>,
        fromListTag: String? = null,
    ) {
        if (users.isEmpty()) return

        val current = audienceMembers ?: emptyList()
        val addition =
            AudienceSelection.addToAudience(
                current = current,
                incoming = users,
                provenance = notifyProvenance,
                fromListTag = fromListTag,
                currentlyMuted = mutedNotifies,
            )

        if (addition.newcomers.isNotEmpty()) {
            audienceMembers = current + addition.newcomers
        }

        // Anyone this add brings into the audience gets their bell back —
        // otherwise the sheet would promise to add somebody and nothing would
        // visibly happen.
        if (mutedNotifies.any { it in addition.unmutes }) {
            mutedNotifies = mutedNotifies - addition.unmutes
        }

        notifyProvenance = addition.provenance

        onAudienceChanged()
    }

    /**
     * Undoes a whole bulk add. People who also arrived from another list, or who
     * were in the audience for an unrelated reason, stay — only the ones this
     * list alone brought in are dropped.
     */
    fun removeListFromAudience(listId: String) {
        val removal = AudienceSelection.removeListFromProvenance(notifyProvenance, listId)
        notifyProvenance = removal.provenance

        if (removal.orphaned.isNotEmpty()) {
            audienceMembers = audienceMembers?.filterNot { it.pubkeyHex in removal.orphaned }?.ifEmpty { null }
            mutedNotifies = mutedNotifies - removal.orphaned
        }

        onAudienceChanged()
    }

    /**
     * Clears the editing surface. The members themselves are owned by the
     * composer, which resets them on its own schedule (a draft load rebuilds
     * them; a cancel drops them).
     */
    fun resetAudienceEditor() {
        wantsToManageAudience = false
        notifyProvenance = emptyMap()
        audienceSearchText.clearText()
    }
}
