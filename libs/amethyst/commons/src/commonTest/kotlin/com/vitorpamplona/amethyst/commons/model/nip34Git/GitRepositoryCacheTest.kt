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

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.nip34Git.gitAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.observeGitAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.observeGitRepositoryActivity
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cache files NIP-34 announcements and items into their repository, and judges statuses through it. */
class GitRepositoryCacheTest {
    private val owner = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()))
    private val coMaintainer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000002".hexToByteArray()))
    private val reporter = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000003".hexToByteArray()))

    private suspend fun announcement(
        signer: NostrSignerInternal,
        other: NostrSignerInternal,
    ) = signer.sign(GitRepositoryEvent.build(name = "din", dTag = "din", createdAt = 10) { add(arrayOf("maintainers", signer.pubKey, other.pubKey)) })

    @Test
    fun repositoriesHoldTheirAnnouncementsAndItemsAndJudgeStatusesThroughThem() =
        runTest {
            val cache = EventCache()
            val ownerAnnouncement = announcement(owner, coMaintainer)
            cache.justConsume(ownerAnnouncement, null, true)

            val issue = reporter.sign(GitIssueEvent.build("Broken", "it broke", EventHintBundle(ownerAnnouncement), emptyList(), emptyList(), createdAt = 20))
            cache.justConsume(issue, null, true)

            val repository = assertNotNull(cache.getGitRepositoryIfExists("din"))
            val itemNote = assertNotNull(cache.getNoteIfExists(issue.id))
            assertTrue(repository.items.containsKey(issue.id))

            // The co-maintainer closes it before their own announcement is in: only the owner is known.
            val close = coMaintainer.sign(GitStatusClosedEvent.build("", EventHintBundle(issue), createdAt = 30))
            cache.justConsume(close, null, true)
            assertNull(GitStatuses.latest(itemNote, cache.gitAuthority))

            // Their announcement confirms them: the same status now counts, with nothing recomputed by hand.
            cache.justConsume(announcement(coMaintainer, owner), null, true)
            assertEquals(close.id, GitStatuses.latest(itemNote, cache.gitAuthority)?.id)
            assertEquals(setOf(owner.pubKey, coMaintainer.pubKey), repository.membership(owner.pubKey)?.confirmedMaintainers?.toSet())

            // The reporter withdraws the issue: the repository lets go of it.
            cache.justConsume(reporter.sign(DeletionRequestEvent.build(listOf(issue))), null, true)
            assertTrue(!repository.items.containsKey(issue.id))
        }

    @Test
    fun theCachesObserversSeeARepositorysActivity() =
        runTest {
            // The observers scan on this test's clock, not on a real thread advanceUntilIdle can't wait for.
            val cache = EventCache().apply { observerScanContext = UnconfinedTestDispatcher(testScheduler) }
            val ownerAnnouncement = announcement(owner, coMaintainer)
            cache.justConsume(ownerAnnouncement, null, true)
            val issue = reporter.sign(GitIssueEvent.build("Broken", "it broke", EventHintBundle(ownerAnnouncement), emptyList(), emptyList(), createdAt = 20))
            cache.justConsume(issue, null, true)

            val emissions = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.observeGitRepositoryActivity("din").collect { emissions.add(it) } }
            advanceUntilIdle()
            val before = emissions.size

            // A status on a listed item, and a co-maintainer's announcement, both reach observers.
            cache.justConsume(coMaintainer.sign(GitStatusClosedEvent.build("", EventHintBundle(issue), createdAt = 30)), null, true)
            advanceUntilIdle()
            val afterStatus = emissions.size
            assertTrue(afterStatus > before, "a status emits")

            cache.justConsume(announcement(coMaintainer, owner), null, true)
            advanceUntilIdle()
            assertTrue(emissions.size > afterStatus, "an announcement emits")
        }

    @Test
    fun theSeedIsTheFlowsFirstList() =
        runTest {
            val cache = EventCache().apply { observerScanContext = UnconfinedTestDispatcher(testScheduler) }
            val ownerAnnouncement = announcement(owner, coMaintainer)
            val coMaintainerAnnouncement = announcement(coMaintainer, owner)
            cache.justConsume(ownerAnnouncement, null, true)
            cache.justConsume(coMaintainerAnnouncement, null, true)
            // A regular event in the same cache, which the announcement read must not walk into.
            cache.justConsume(reporter.sign(GitIssueEvent.build("Broken", "it broke", EventHintBundle(ownerAnnouncement), emptyList(), emptyList(), createdAt = 20)), null, true)

            val seed = cache.gitAnnouncements("din")
            assertEquals(setOf(ownerAnnouncement.id, coMaintainerAnnouncement.id), seed.map { it.id }.toSet())
            // Equal, order included: a card seeded with it doesn't recompute when the flow catches up.
            assertEquals(cache.observeGitAnnouncements("din").first(), seed)
            assertEquals(emptyList(), cache.gitAnnouncements("unknown"))
        }
}
