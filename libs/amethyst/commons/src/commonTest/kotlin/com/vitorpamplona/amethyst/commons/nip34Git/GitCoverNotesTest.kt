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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class GitCoverNotesTest {
    private val sig = "0".repeat(128)
    private val root = "a".repeat(64)
    private val otherRoot = "b".repeat(64)
    private val author = "1".repeat(64)
    private val owner = "2".repeat(64)
    private val maintainer = "3".repeat(64)
    private val stranger = "4".repeat(64)

    // The owner and the maintainer list each other, so both are confirmed members.
    private fun announcement(
        pubKey: String,
        other: String,
    ) = GitRepositoryEvent(pubKey.take(63) + "0", pubKey, 1, arrayOf(arrayOf("d", "repo"), arrayOf("maintainers", pubKey, other)), "", sig)

    private val authority = GitAuthority.of(mapOf("repo" to mapOf(owner to announcement(owner, maintainer), maintainer to announcement(maintainer, owner))))

    private val item = GitPullRequestEvent(root, author, 1, arrayOf(arrayOf("a", "30617:$owner:repo")), "", sig)

    private fun latest(notes: List<GitCoverNoteEvent>) = GitCoverNotes.authorised(notes, item, authority).lastOrNull()

    private fun note(
        id: String,
        pubKey: String,
        createdAt: Long,
        target: String = root,
        marker: Boolean = true,
    ): GitCoverNoteEvent {
        val eTag = if (marker) arrayOf("e", target, "", "root") else arrayOf("e", target)
        return GitCoverNoteEvent(id, pubKey, createdAt, arrayOf(eTag, arrayOf("p", author), arrayOf("k", "1618")), "**note** $id", sig)
    }

    @Test
    fun latestAuthorisedNoteWins() {
        val old = note("1".repeat(64), author, 100)
        val newer = note("2".repeat(64), maintainer, 200)

        assertSame(newer, latest(listOf(newer, old)))
    }

    @Test
    fun notesByOtherPubkeysAreIgnoredEvenWhenNewer() {
        val mine = note("1".repeat(64), author, 100)
        val spam = note("2".repeat(64), stranger, 900)

        assertSame(mine, latest(listOf(spam, mine)))
        assertNull(latest(listOf(spam)))
    }

    @Test
    fun tiesAreBrokenByTheHigherEventId() {
        val low = note("1".repeat(64), author, 100)
        val high = note("f".repeat(64), owner, 100)

        assertSame(high, latest(listOf(low, high)))
        assertSame(high, latest(listOf(high, low)))
    }

    @Test
    fun onlyNotesForThisItemCount() {
        val elsewhere = note("1".repeat(64), author, 900, target = otherRoot)
        val here = note("2".repeat(64), author, 100)

        assertSame(here, latest(listOf(elsewhere, here)))
    }

    @Test
    fun unmarkedRootTagSeenOnRelaysStillResolves() {
        val unmarked = note("1".repeat(64), author, 100, marker = false)

        assertSame(unmarked, latest(listOf(unmarked)))
    }
}
