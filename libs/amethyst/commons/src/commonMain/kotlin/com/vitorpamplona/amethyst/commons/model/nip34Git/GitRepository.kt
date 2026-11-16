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
package com.vitorpamplona.amethyst.commons.model.nip34Git

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NotesGatherer
import com.vitorpamplona.amethyst.commons.model.cache.LargeSoftCache
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership

/**
 * Everything the cache knows about the NIP-34 repositories published under one identifier (the
 * announcements' `d`): each author's announcement, and every issue, patch and PR filed under any
 * `30617:<author>:<identifier>` coordinate. Held in `EventCache.gitRepositories`, as channels are.
 *
 * One identifier can hold unrelated repositories (two people who both called theirs `ngit`).
 * They are told apart by [membership], resolved from a selected author's announcement the way
 * gitworkshop does, so a stranger's announcement under the same name never joins another's.
 *
 * It holds its notes softly, as the cache does: a screen that shows them keeps them alive, and
 * memory pressure can reclaim the rest. Statuses, PR updates, cover notes and renames are not
 * stored here: the cache threads each one onto the item it acts on (its replies). To react to any
 * of it, observe the cache ([com.vitorpamplona.amethyst.commons.model.cache.EventCache.observeEvents]).
 */
@Stable
class GitRepository(
    val identifier: String,
) : NotesGatherer {
    private val announcementNotes = LargeSoftCache<HexKey, AddressableNote>()

    /** The issues, patches and pull requests filed under one of this identifier's coordinates. */
    val items = LargeSoftCache<HexKey, Note>()

    private val lock = KmpLock()

    // Memberships resolved from the announcements as they stand, by selected author. Resolving
    // walks the role graph of every announcement involved, so it runs once per change, not per check.
    private var memberships = HashMap<HexKey, GitRepositoryMembership>()

    // Bumped whenever an announcement changes, so a resolution that started before the change is
    // not stored after it.
    private var announcementsVersion = 0L

    /** Each author's latest announcement under this identifier. */
    fun announcements(): Map<HexKey, GitRepositoryEvent> =
        buildMap {
            announcementNotes.forEach { author, note -> (note.event as? GitRepositoryEvent)?.let { put(author, it) } }
        }

    /** [author]'s announcement under this identifier, when it has loaded. */
    fun announcement(author: HexKey): GitRepositoryEvent? = announcementNotes.get(author)?.event as? GitRepositoryEvent

    /**
     * The membership of the repository [selectedAuthor]'s announcement belongs to, seen from its
     * lead, or null while that announcement is unknown.
     */
    fun membership(selectedAuthor: HexKey): GitRepositoryMembership? {
        val version =
            lock.withLock {
                memberships[selectedAuthor]?.let { return it }
                announcementsVersion
            }
        val resolved = GitRepositoryMembership.resolveFromLead(selectedAuthor, identifier, announcements()) ?: return null
        lock.withLock { if (version == announcementsVersion) memberships[selectedAuthor] = resolved }
        return resolved
    }

    /** Called when [note], an announcement under this identifier, arrives or is replaced by a newer version. */
    fun addAnnouncement(note: AddressableNote) {
        if (announcementNotes.get(note.address.pubKeyHex) !== note) {
            announcementNotes.put(note.address.pubKeyHex, note)
            note.addGatherer(this)
        }
        // Any announcement can confirm or end anyone's role, so every resolved view is stale.
        forgetMemberships()
    }

    /** Called when [note], an issue, patch or PR filed under this identifier, arrives. */
    fun addItem(note: Note) {
        if (items.containsKey(note.idHex)) return
        items.put(note.idHex, note)
        note.addGatherer(this)
    }

    override fun removeNote(note: Note) {
        if (items.containsKey(note.idHex)) {
            items.remove(note.idHex)
        } else if (note is AddressableNote && announcementNotes.get(note.address.pubKeyHex) === note) {
            announcementNotes.remove(note.address.pubKeyHex)
            forgetMemberships()
        }
    }

    private fun forgetMemberships() =
        lock.withLock {
            announcementsVersion++
            memberships = HashMap()
        }
}
