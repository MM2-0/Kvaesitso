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
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeInputs
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeTargets
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitMergeInputsTest {
    private val sig = "0".repeat(128)
    private val owner = "1".repeat(64)
    private val author = "4".repeat(64)
    private val stranger = "5".repeat(64)
    private val coordinate = Address(GitRepositoryEvent.KIND, owner, "din")
    private val npub = "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"

    private fun repo(
        pubKey: HexKey,
        vararg tags: Array<String>,
    ) = GitRepositoryEvent(pubKey.take(2).repeat(32), pubKey, 1, arrayOf(arrayOf("d", "din"), *tags), "", sig)

    private val announcement =
        repo(
            owner,
            arrayOf("clone", "https://relay.ngit.dev/$npub/din.git", "https://github.com/x/din"),
            arrayOf("relays", "wss://relay.ngit.dev", "wss://nos.lol"),
        )
    private val authority = GitAuthority.of(mapOf("din" to GitRepositoryMembership.latestByAuthor(listOf(announcement), "din")))

    private fun build(
        item: Event,
        threaded: List<Event> = emptyList(),
        updates: List<GitPullRequestUpdateEvent> = emptyList(),
        series: (GitPatchEvent) -> List<GitPatchEvent> = { listOf(it) },
    ) = GitMergeInputs.build(item, threaded, series, { updates }, coordinate, listOf(announcement), emptyList(), authority, null, null)

    @Test
    fun aRevisedSeriesMergesItsLatestRevisionButKeepsTheOriginalsTitle() {
        val root = GitPatchEvent("a".repeat(64), author, 10, arrayOf(arrayOf("a", coordinate.toValue()), arrayOf("t", "root"), arrayOf("description", "Original title\n\nbody")), "", sig)
        val revision = GitPatchEvent("b".repeat(64), author, 20, arrayOf(arrayOf("t", "root"), arrayOf("t", "root-revision"), arrayOf("e", root.id, "", "reply")), "", sig)
        val second = GitPatchEvent("c".repeat(64), author, 21, arrayOf(arrayOf("e", revision.id, "", "root")), "", sig)
        val inputs = build(root, listOf(revision), series = { if (it.id == revision.id) listOf(revision, second) else listOf(it) })
        assertEquals(root, inputs.item)
        assertEquals(revision.id, inputs.source.id)
        assertEquals(listOf(revision.id, second.id), inputs.patches.map { it.id })
        assertEquals("Original title", inputs.title)
    }

    @Test
    fun aPullRequestUsesItsAuthorisedUpdateCoverNoteAndRename() {
        val pr = GitPullRequestEvent("a".repeat(64), author, 10, arrayOf(arrayOf("a", coordinate.toValue()), arrayOf("subject", "Old"), arrayOf("c", "1".repeat(40)), arrayOf("clone", "https://fork.example/x.git")), "PR body", sig)
        val update = GitPullRequestUpdateEvent("b".repeat(64), author, 20, arrayOf(arrayOf("E", pr.id), arrayOf("c", "2".repeat(40)), arrayOf("clone", "https://fork.example/y.git")), "", sig)
        val strangersUpdate = GitPullRequestUpdateEvent("c".repeat(64), stranger, 30, arrayOf(arrayOf("E", pr.id), arrayOf("c", "3".repeat(40))), "", sig)
        val cover = GitCoverNoteEvent("d".repeat(64), owner, 15, arrayOf(arrayOf("e", pr.id, "", "root")), "The cover note", sig)
        val rename = LabelEvent("e".repeat(64), owner, 16, arrayOf(arrayOf("e", pr.id), arrayOf("L", "#subject"), arrayOf("l", "New title", "#subject")), "", sig)
        val inputs = build(pr, listOf(cover, rename), updates = listOf(update, strangersUpdate))
        assertEquals("2".repeat(40), inputs.pullRequestTip)
        assertEquals(listOf("https://fork.example/y.git", "https://fork.example/x.git"), inputs.pullRequestClones)
        assertEquals("The cover note", inputs.description)
        assertTrue(inputs.descriptionIsCoverNote)
        assertEquals("New title", inputs.title)
    }

    @Test
    fun aGitHubMirrorIsNotPushedButDoesNotBlockTheMerge() {
        val targets = GitMergeTargets.of(listOf(announcement))
        assertEquals(listOf("https://relay.ngit.dev/$npub/din.git"), targets.grasp.map { it.cloneUrl })
        assertEquals(listOf("https://github.com/x/din"), targets.others)
        assertEquals(emptyList(), GitMergeTargets.of(listOf(repo(owner, arrayOf("clone", "https://github.com/x/din")))).grasp)
    }
}
