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
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitAuthorityTest {
    private val sig = "0".repeat(128)
    private val owner = "1".repeat(64)
    private val coMaintainer = "2".repeat(64)
    private val invitee = "3".repeat(64)
    private val author = "4".repeat(64)
    private val stranger = "5".repeat(64)
    private val itemId = "a".repeat(64)

    private fun repo(
        pubKey: HexKey,
        vararg maintainers: HexKey,
    ) = GitRepositoryEvent(pubKey.take(2).repeat(32), pubKey, 1, arrayOf(arrayOf("d", "din"), arrayOf("maintainers", *maintainers)), "", sig)

    private val announcements =
        listOf(
            repo(owner, coMaintainer, invitee),
            repo(coMaintainer, owner),
            // The invitee never lists anyone back: invited, not confirmed.
            repo(invitee),
        )

    private val authority = GitAuthority.of(mapOf("din" to GitRepositoryMembership.latestByAuthor(announcements, "din")))

    private val issue = GitIssueEvent(itemId, author, 10, arrayOf(arrayOf("a", "30617:$owner:din")), "bug", sig)

    private fun status(
        id: String,
        pubKey: HexKey,
        createdAt: Long,
        closed: Boolean,
    ): GitStatusEvent {
        val tags = arrayOf(arrayOf("e", itemId, "", "root"))
        return if (closed) GitStatusClosedEvent(id, pubKey, createdAt, tags, "", sig) else GitStatusOpenEvent(id, pubKey, createdAt, tags, "", sig)
    }

    @Test
    fun membersAreTheAuthorAndConfirmedMaintainersOnly() {
        assertEquals(setOf(author, owner, coMaintainer), authority.memberActorsOf(issue))
    }

    @Test
    fun anUnloadedRepositoryFallsBackToItsOwner() {
        assertEquals(setOf(author, owner), GitAuthority.EMPTY.memberActorsOf(issue))
    }

    @Test
    fun aStrangersCloseDoesNotCloseTheIssue() {
        val items = mapOf<HexKey, Event>(itemId to issue)
        val open = status("b".repeat(64), coMaintainer, 20, closed = false)
        val strangerClose = status("c".repeat(64), stranger, 30, closed = true)
        val inviteeClose = status("d".repeat(64), invitee, 40, closed = true)

        val latest = GitStatuses.latestByItem(listOf(open, strangerClose, inviteeClose), items, authority)
        assertEquals(open, latest[itemId])

        val authorClose = status("e".repeat(64), author, 50, closed = true)
        assertEquals(authorClose, GitStatuses.latestByItem(listOf(open, strangerClose, authorClose), items, authority)[itemId])
    }

    @Test
    fun statusesWaitForTheirItem() {
        val close = status("b".repeat(64), owner, 20, closed = true)
        assertNull(GitStatuses.latestByItem(listOf(close), emptyMap(), authority)[itemId])
    }

    @Test
    fun onlyTheAuthorAndMaintainersCanMoveAPullRequest() {
        val pr = GitPullRequestEvent(itemId, author, 10, arrayOf(arrayOf("a", "30617:$owner:din"), arrayOf("c", "aaaaaaa")), "", sig)

        fun update(
            id: String,
            pubKey: HexKey,
            commit: String,
        ) = GitPullRequestUpdateEvent(id, pubKey, 20, arrayOf(arrayOf("E", itemId), arrayOf("c", commit)), "", sig)

        val fromAuthor = update("b".repeat(64), author, "bbbbbbb")
        val fromMaintainer = update("c".repeat(64), coMaintainer, "ccccccc")
        val fromStranger = update("d".repeat(64), stranger, "ddddddd")

        val kept = GitPullRequestRevisions.authorised(listOf(fromStranger, fromAuthor, fromMaintainer), pr, authority)
        assertEquals(setOf(fromAuthor, fromMaintainer), kept.toSet())
        assertTrue(fromStranger !in kept)
    }

    @Test
    fun aMergeBySomeoneWhoBecameAMaintainerLaterIsOnlyAProposal() {
        // The real `din` repository: Alex (0461) marked chad's July PR merged, but the lead's
        // announcement records Alex as a maintainer only from Nov 2. gitworkshop keeps it open.
        val alex = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"
        val soapbox = "781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5"
        val chad = "86184109eae937d8d6f980b4a0b46da4ef0d983eade403ee1b4c0b6bde238b47"
        val din =
            listOf(
                GitRepositoryEvent(
                    "573aab8572fbb42cbc143a4d3594a02e4b3bc86da3d3d67adb6f8840f71ea554",
                    alex,
                    1793647335,
                    arrayOf(
                        arrayOf("d", "din"),
                        arrayOf("maintainers", alex, soapbox),
                        arrayOf("m", alex),
                        arrayOf("M", soapbox, "1793647289"),
                        arrayOf("m", soapbox, "1793647060", "1793647289"),
                        arrayOf("m", chad, "0", "1793647255", "1793647255", "defer"),
                    ),
                    "",
                    sig,
                ),
                GitRepositoryEvent(
                    "ef23a3f7bb6cc75aec878ce06d2d23276fc5105dded2abbb9d2d17da949d8085",
                    soapbox,
                    1793647255,
                    arrayOf(
                        arrayOf("d", "din"),
                        arrayOf("maintainers", soapbox, alex, chad),
                        arrayOf("M", soapbox, "1793647255"),
                        arrayOf("m", soapbox, "1793647172", "1793647255"),
                        arrayOf("m", alex, "1793647172"),
                        arrayOf("m", chad, "0", "1793647255", "1793647255"),
                    ),
                    "",
                    sig,
                ),
                GitRepositoryEvent("7540f16c85a1bce5870aac78b03d4e53b52f5b88da2b7c814b50ff23abc50eec", chad, 1784466593, arrayOf(arrayOf("d", "din"), arrayOf("maintainers", chad, alex)), "", sig),
            )
        val dinAuthority = GitAuthority.of(mapOf("din" to GitRepositoryMembership.latestByAuthor(din, "din")))
        val julyPr = GitPullRequestEvent(itemId, chad, 1784429323, arrayOf(arrayOf("a", "30617:$alex:din")), "", sig)
        val julyMerge = GitStatusClosedEvent("b".repeat(64), alex, 1784431506, arrayOf(arrayOf("e", itemId, "", "root")), "", sig)

        assertTrue(alex in dinAuthority.memberActorsOf(julyPr), "Alex is a maintainer today")
        assertNull(GitStatuses.latestByItem(listOf(julyMerge), mapOf(itemId to julyPr), dinAuthority)[itemId])
    }
}
