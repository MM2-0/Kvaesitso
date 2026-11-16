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
package com.vitorpamplona.amethyst.commons.nip34Git.coverNote

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent

/**
 * Cover notes (kind 1624) on NIP-34 issues, patches and PRs, by gitworkshop's rule: a note counts
 * when the item's author or a confirmed repository member signed it, by the roles when it was
 * signed ([GitAuthority.isMemberAt]). The newest of those is shown above the description, and the
 * older ones are its earlier versions.
 */
object GitCoverNotes {
    /**
     * Oldest first; at equal timestamps the higher id is the later one, as gitworkshop sorts them
     * (`resolveCoverNotes`), so [latest] is the one it shows.
     */
    private val order = compareBy<GitCoverNoteEvent> { it.createdAt }.thenBy { it.id }

    /** Every cover note of [item] that counts, oldest first: the versions its summary steps through. */
    fun versions(
        item: Note,
        authority: GitAuthority,
    ): List<GitCoverNoteEvent> {
        val itemEvent = item.event ?: return emptyList()
        return authorised(item.replies.mapNotNull { it.event as? GitCoverNoteEvent }, itemEvent, authority)
    }

    /** The cover note shown on [item], or null when no one who counts has set one. */
    fun latest(
        item: Note,
        authority: GitAuthority,
    ): GitCoverNoteEvent? = versions(item, authority).lastOrNull()

    /** [versions] for notes held outside the cache. */
    fun authorised(
        notes: Collection<GitCoverNoteEvent>,
        item: Event,
        authority: GitAuthority,
    ): List<GitCoverNoteEvent> =
        notes
            .filter { it.rootEventId() == item.id && authority.isMemberAt(it.pubKey, item, it.createdAt) }
            .sortedWith(order)
}
