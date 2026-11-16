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

import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevision
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitPullRequestRevisionsTest {
    private val sig = "0".repeat(128)
    private val prId = "a".repeat(64)
    private val author = "1".repeat(64)

    private val pullRequest =
        GitPullRequestEvent(
            prId,
            author,
            100,
            arrayOf(arrayOf("c", "70d31ba"), arrayOf("merge-base", "7195a21")),
            "",
            sig,
        )

    private fun update(
        id: String,
        createdAt: Long,
        commit: String,
        mergeBase: String,
    ) = GitPullRequestUpdateEvent(
        id,
        author,
        createdAt,
        arrayOf(arrayOf("E", prId), arrayOf("c", commit), arrayOf("merge-base", mergeBase)),
        "",
        sig,
    )

    @Test
    fun firstUpdateReplacesThePullRequestsOwnTip() {
        val first = update("1".repeat(64), 200, "4246e71", "bca8dc9")

        val previous = GitPullRequestRevisions.previous(first, listOf(first), pullRequest)

        assertEquals(GitPullRequestRevision("70d31ba", "7195a21"), previous)
        assertTrue(GitPullRequestRevision.of(first).rebasedFrom(previous!!))
    }

    @Test
    fun laterUpdateReplacesTheUpdateBeforeIt() {
        val first = update("1".repeat(64), 200, "4246e71", "bca8dc9")
        val second = update("2".repeat(64), 300, "9999999", "bca8dc9")

        val previous = GitPullRequestRevisions.previous(second, listOf(second, first), pullRequest)

        assertEquals(GitPullRequestRevision("4246e71", "bca8dc9"), previous)
        assertFalse(GitPullRequestRevision.of(second).rebasedFrom(previous!!))
    }

    @Test
    fun updatesAtTheSameSecondCountTheLowerIdAsLater() {
        // gitworkshop's tie-break: at equal created_at the lower id wins, so it is the later tip.
        val low = update("1".repeat(64), 200, "1111111", "bca8dc9")
        val high = update("f".repeat(64), 200, "fffffff", "bca8dc9")

        assertEquals("fffffff", GitPullRequestRevisions.previous(low, listOf(high, low), pullRequest)?.commit)
        assertEquals("70d31ba", GitPullRequestRevisions.previous(high, listOf(high, low), pullRequest)?.commit)
    }

    @Test
    fun unknownWhenThePullRequestHasNotLoaded() {
        val first = update("1".repeat(64), 200, "4246e71", "bca8dc9")

        assertNull(GitPullRequestRevisions.previous(first, listOf(first), null))
    }

    @Test
    fun aMissingMergeBaseIsNotARebase() {
        val noBase = GitPullRequestRevision("4246e71", null)

        assertFalse(noBase.rebasedFrom(GitPullRequestRevision("70d31ba", "7195a21")))
        assertFalse(GitPullRequestRevision("4246e71", "bca8dc9").rebasedFrom(noBase))
    }
}
