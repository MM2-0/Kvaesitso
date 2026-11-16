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
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitMergeStatuses
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitMergeStatusesTest {
    private val sig = "0".repeat(128)
    private val owner = "1".repeat(64)
    private val coMaintainer = "2".repeat(64)
    private val author = "4".repeat(64)
    private val stranger = "5".repeat(64)
    private val coordinate = Address(GitRepositoryEvent.KIND, owner, "din")
    private val otherRepo = Address(GitRepositoryEvent.KIND, stranger, "other")

    private fun repo(
        pubKey: HexKey,
        vararg maintainers: HexKey,
    ) = GitRepositoryEvent(pubKey.take(2).repeat(32), pubKey, 1, arrayOf(arrayOf("d", "din"), arrayOf("maintainers", *maintainers)), "", sig)

    private val authority =
        GitAuthority.of(mapOf("din" to GitRepositoryMembership.latestByAuthor(listOf(repo(owner, coMaintainer), repo(coMaintainer, owner)), "din")))

    private fun issue(
        id: String,
        repository: Address = coordinate,
    ) = GitIssueEvent(id, author, 10, arrayOf(arrayOf("a", repository.toValue())), "bug", sig)

    private val pr = GitPullRequestEvent("f".repeat(64), author, 20, arrayOf(arrayOf("a", coordinate.toValue())), "", sig)

    @Test
    fun aMergedCommitResolvesEachOpenIssueItNamesOnce() {
        val open = issue("ab12cd34" + "0".repeat(56))
        val closed = issue("ef56ab78" + "0".repeat(56))
        val elsewhere = issue("9988aabb" + "0".repeat(56), otherRepo)
        val commits =
            listOf(
                GitMergeStatuses.MergedCommit("c1", "Parse it\n\nFixes #ab12cd34 and #ef56ab78, closes #9988aabb"),
                GitMergeStatuses.MergedCommit("c2", "Again\n\nresolves #ab12cd34"),
            )
        val resolutions = GitMergeStatuses.resolutions(commits, listOf(open, closed, elsewhere), setOf(coordinate)) { it.id != closed.id }
        assertEquals(listOf(open.id), resolutions.map { it.issue.id })
        assertEquals("c1", resolutions.single().commit)
        assertEquals("Fixes #ab12cd34 and #ef56ab78, closes #9988aabb", resolutions.single().line)
    }

    @Test
    fun anAmbiguousPrefixResolvesNothing() {
        val a = issue("ab12cd34" + "1".repeat(56))
        val b = issue("ab12cd34" + "2".repeat(56))
        val commits = listOf(GitMergeStatuses.MergedCommit("c1", "fix #ab12cd34"))
        assertEquals(emptyList(), GitMergeStatuses.resolutions(commits, listOf(a, b), setOf(coordinate)) { true })
    }

    @Test
    fun theMergedStatusTagsTheRepositoryAndQuotesThePatches() {
        val patch = GitPatchEvent("e".repeat(64), author, 20, arrayOf(arrayOf("a", coordinate.toValue())), "", sig)
        val template =
            GitMergeStatuses.merged(
                target = EventHintBundle<Event>(patch),
                coordinates = listOf(coordinate, Address(GitRepositoryEvent.KIND, coMaintainer, "din")),
                earliestUniqueCommit = "a".repeat(40),
                signer = owner,
                mergeCommit = "b".repeat(40),
                appliedPatches = listOf(EventHintBundle(patch)),
            )
        val tags = template.tags.toList()
        assertEquals(listOf(coordinate.toValue(), "30617:$coMaintainer:din"), tags.filter { it[0] == "a" }.map { it[1] })
        // The signer is not notified of their own merge; the author and the other maintainer are.
        assertEquals(setOf(author, coMaintainer), tags.filter { it[0] == "p" }.map { it[1] }.toSet())
        assertEquals(listOf(patch.id), tags.filter { it[0] == "q" }.map { it[1] })
        assertEquals("b".repeat(40), tags.first { it[0] == "merge-commit" }[1])
        assertEquals(setOf("a".repeat(40), "b".repeat(40)), tags.filter { it[0] == "r" }.map { it[1] }.toSet())
    }

    @Test
    fun theResolvedStatusQuotesTheLineAndThePullRequest() {
        val issue = issue("ab12cd34" + "0".repeat(56))
        val resolution = GitMergeStatuses.Resolution(issue, "c".repeat(40), "Fixes #ab12cd34")
        val template =
            GitMergeStatuses.resolved(resolution, EventHintBundle<Event>(issue), "d".repeat(40), EventHintBundle<Event>(pr), listOf(coordinate), null, owner)
        assertEquals("Fixes #ab12cd34\n\nresolved by commit ${"c".repeat(40)}, when merged in commit ${"d".repeat(40)}", template.content)
        val tags = template.tags.toList()
        assertEquals("c".repeat(40), tags.first { it[0] == "c" }[1])
        assertEquals(pr.id, tags.first { it[0] == "q" }[1])
        assertEquals("issue resolved from commit message", tags.first { it[0] == "alt" }[1])
        assertEquals(issue.id, tags.first { it[0] == "e" }[1])
    }

    @Test
    fun theLatestRevisionByTheAuthorOrAMaintainerIsMerged() {
        val root = GitPatchEvent("a".repeat(64), author, 10, arrayOf(arrayOf("a", coordinate.toValue()), arrayOf("t", "root")), "", sig)

        fun revision(
            id: Char,
            pubKey: HexKey,
            createdAt: Long,
            spelling: String = "root-revision",
        ) = GitPatchEvent(
            id.toString().repeat(64),
            pubKey,
            createdAt,
            arrayOf(arrayOf("a", coordinate.toValue()), arrayOf("t", "root"), arrayOf("t", spelling), arrayOf("e", root.id, "", "reply")),
            "",
            sig,
        )
        val byAuthor = revision('b', author, 20)
        val byMaintainer = revision('c', coMaintainer, 30, spelling = "revision-root")
        val byStranger = revision('d', stranger, 40)
        val notARevision = GitPatchEvent("e".repeat(64), author, 50, arrayOf(arrayOf("e", root.id, "", "reply")), "", sig)

        assertEquals(byMaintainer.id, GitPatchSeries.latestRevision(root, listOf(byAuthor, byMaintainer, byStranger, notARevision), authority)?.id)
        assertNull(GitPatchSeries.latestRevision(root, listOf(byStranger, notARevision), authority))
    }
}
