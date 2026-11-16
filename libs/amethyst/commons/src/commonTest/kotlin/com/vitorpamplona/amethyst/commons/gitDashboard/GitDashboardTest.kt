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
package com.vitorpamplona.amethyst.commons.gitDashboard

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GitDashboardTest {
    private val me = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()))
    private val coMaintainer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000002".hexToByteArray()))
    private val stranger = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000003".hexToByteArray()))

    @Test
    fun theInboxHoldsWhatIsFiledInWatchedRepositoriesAndWhatTheReaderWrote() =
        runTest {
            val cache = EventCache()
            val mine = me.sign(GitRepositoryEvent.build(name = "din", dTag = "din", createdAt = 10))
            val theirs = stranger.sign(GitRepositoryEvent.build(name = "other", dTag = "other", createdAt = 10))
            cache.justConsume(mine, null, true)
            cache.justConsume(theirs, null, true)

            val inMine = stranger.sign(GitIssueEvent.build("Broken", "it broke", EventHintBundle(mine), emptyList(), emptyList(), createdAt = 20))
            val inTheirs = stranger.sign(GitIssueEvent.build("Elsewhere", "not mine", EventHintBundle(theirs), emptyList(), emptyList(), createdAt = 21))
            val mineElsewhere = me.sign(GitIssueEvent.build("My report", "I filed it", EventHintBundle(theirs), emptyList(), emptyList(), createdAt = 22))
            listOf(inMine, inTheirs, mineElsewhere).forEach { cache.justConsume(it, null, true) }

            assertEquals(listOf("din"), GitDashboard.myRepositories(me.pubKey, cache).map { it.note.address.dTag })

            val inbox = GitDashboard.inbox(me.pubKey, listOf(mine.address()), cache, lastRead = { 0L })
            assertEquals(setOf(inMine.id, mineElsewhere.id), inbox.map { it.item.idHex }.toSet())
            assertEquals(GitInvolvement.WATCHING, inbox.first { it.item.idHex == inMine.id }.involvement)
            assertEquals(GitInvolvement.AUTHOR, inbox.first { it.item.idHex == mineElsewhere.id }.involvement)
            // The reader's own report isn't news to them; the stranger's is.
            assertEquals(0, inbox.first { it.item.idHex == mineElsewhere.id }.unread)
            assertEquals(1, inbox.first { it.item.idHex == inMine.id }.unread)
            assertEquals(listOf(mineElsewhere.id), inbox.filter { it.isInvolved }.map { it.item.idHex })
            assertEquals(1, GitDashboard.myRepositories(me.pubKey, cache).single().openIssues)
        }

    @Test
    fun activityAfterTheReaderOpenedAnItemIsUnreadAndMovesItUp() =
        runTest {
            val cache = EventCache()
            val mine = me.sign(GitRepositoryEvent.build(name = "din", dTag = "din", createdAt = 10))
            cache.justConsume(mine, null, true)
            val older = stranger.sign(GitIssueEvent.build("Older", "", EventHintBundle(mine), emptyList(), emptyList(), createdAt = 20))
            val newer = stranger.sign(GitIssueEvent.build("Newer", "", EventHintBundle(mine), emptyList(), emptyList(), createdAt = 25))
            cache.justConsume(older, null, true)
            cache.justConsume(newer, null, true)

            // Read both at 26, then the stranger closes the older one.
            cache.justConsume(stranger.sign(GitStatusClosedEvent.build("", EventHintBundle(older), createdAt = 30)), null, true)
            val inbox = GitDashboard.inbox(me.pubKey, listOf(mine.address()), cache, lastRead = { 26L })

            assertEquals(listOf(older.id, newer.id), inbox.map { it.item.idHex })
            assertEquals(1, inbox[0].unread)
            assertEquals(30L, inbox[0].lastActivity)
            assertEquals(0, inbox[1].unread)
            assertEquals(GitInboxAction.CLOSED, inbox[0].latestAction)
            assertEquals(GitInboxAction.OPENED, inbox[1].latestAction)
        }

    @Test
    fun itemsFiledUnderACoMaintainersCoordinateReachTheInboxThroughTheGivenCache() =
        runTest {
            // A fresh cache, not LocalCache: membership must be read from the cache passed in.
            val cache = EventCache()
            val mine = me.sign(GitRepositoryEvent.build(name = "din", dTag = "din", createdAt = 10) { add(arrayOf("maintainers", me.pubKey, coMaintainer.pubKey)) })
            val theirs = coMaintainer.sign(GitRepositoryEvent.build(name = "din", dTag = "din", createdAt = 11) { add(arrayOf("maintainers", coMaintainer.pubKey, me.pubKey)) })
            cache.justConsume(mine, null, true)
            cache.justConsume(theirs, null, true)

            val filedWithThem = stranger.sign(GitIssueEvent.build("Theirs", "", EventHintBundle(theirs), emptyList(), emptyList(), createdAt = 20))
            cache.justConsume(filedWithThem, null, true)

            val inbox = GitDashboard.inbox(me.pubKey, listOf(mine.address()), cache, lastRead = { 0L })
            assertEquals(listOf(filedWithThem.id), inbox.map { it.item.idHex })
            assertEquals(1, GitDashboard.myRepositories(me.pubKey, cache).single().openIssues)
        }
}
