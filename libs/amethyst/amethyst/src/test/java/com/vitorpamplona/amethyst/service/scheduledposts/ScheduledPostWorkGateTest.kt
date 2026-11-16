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
package com.vitorpamplona.amethyst.service.scheduledposts

import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostWorkGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The gate must keep the periodic worker enqueued exactly while a PENDING
 * post exists — and, critically, must never act on the store flow's empty
 * placeholder value before the disk load completes (which would cancel
 * scheduled work an existing pending post still needs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledPostWorkGateTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var file: File

    /** Chronological record of gate decisions: true = schedule, false = cancel. */
    private val decisions = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        file = File(temp.root, "scheduled_posts.json")
        decisions.clear()
    }

    private fun newStore() = ScheduledPostStore(file.path)

    private fun TestScope.startGate(store: ScheduledPostStore) =
        ScheduledPostWorkGate(
            store = store,
            scope = backgroundScope,
            onPendingWork = { decisions.add(true) },
            onNoPendingWork = { decisions.add(false) },
        ).start()

    private fun samplePost(
        id: String = "id-1",
        publishAtSec: Long = 4_000_000_000,
    ) = ScheduledPost(
        id = id,
        accountPubkey = "pk1",
        signedEventJson = "{}",
        relayUrls = listOf("wss://relay.example/"),
        extraEventsJson = emptyList(),
        publishAtSec = publishAtSec,
        createdAtSec = 500,
    )

    @Test
    fun emptyStore_cancelsOnceAndOnlyOnce() =
        runTest {
            startGate(newStore())
            runCurrent()
            assertEquals(listOf(false), decisions)
        }

    @Test
    fun pendingPostOnDisk_schedulesWithoutSpuriousCancelFirst() =
        runTest {
            // Persist a pending post in a previous "process".
            runBlocking { newStore().add(samplePost()) }

            // Fresh store (flow starts as the empty placeholder). The gate must
            // load from disk before collecting: the FIRST decision must be
            // "schedule", never a cancel from the placeholder value.
            startGate(newStore())
            runCurrent()
            assertEquals(listOf(true), decisions)
        }

    @Test
    fun addThenDrainThenAddAgain_togglesTheWorker() =
        runTest {
            val store = newStore()
            startGate(store)
            runCurrent()
            assertEquals(listOf(false), decisions)

            store.add(samplePost(id = "a", publishAtSec = 1_000))
            runCurrent()
            assertEquals(listOf(false, true), decisions)

            // A second pending post must not re-fire (distinctUntilChanged).
            store.add(samplePost(id = "b", publishAtSec = 1_000))
            runCurrent()
            assertEquals(listOf(false, true), decisions)

            // Draining both pending posts cancels the periodic chain. A row must be
            // claimed (PENDING -> PUBLISHING) before it can be marked terminal — the
            // store only allows markSent/markFailed from PUBLISHING, mirroring the
            // real worker flow.
            store.claimDuePosts(nowSec = 2_000)
            runCurrent()
            assertEquals(listOf(false, true), decisions)
            store.markSent("a")
            runCurrent()
            assertEquals(listOf(false, true), decisions)
            store.markFailed("b", "boom")
            runCurrent()
            assertEquals(listOf(false, true, false), decisions)

            // A new post re-schedules.
            store.add(samplePost(id = "c"))
            runCurrent()
            assertEquals(listOf(false, true, false, true), decisions)
        }

    @Test
    fun claimingTheLastPendingPost_doesNotCancelTheRunningWorker() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a", publishAtSec = 1_000))
            startGate(store)
            runCurrent()
            assertEquals(listOf(true), decisions)

            // The periodic worker claims the post (PENDING -> PUBLISHING) and
            // is now mid-publish. Cancelling here would kill the very worker
            // holding the claim and strand the post in PUBLISHING forever.
            store.claimDuePosts(nowSec = 2_000)
            runCurrent()
            assertEquals("a claim must never cancel the chain", listOf(true), decisions)

            // Only the publish actually finishing drains the gate.
            store.markSent("a")
            runCurrent()
            assertEquals(listOf(true, false), decisions)
        }

    @Test
    fun publishNowOnFailedPost_reschedulesTheWorker() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a", publishAtSec = 1_000))
            startGate(store)
            runCurrent()
            // The worker claims (PENDING -> PUBLISHING) then fails the publish. A row
            // may only be marked FAILED from PUBLISHING, so claim it first.
            store.claimDuePosts(nowSec = 2_000)
            runCurrent()
            store.markFailed("a", "relay down")
            runCurrent()
            assertEquals(listOf(true, false), decisions)

            // Retry flips the post back to PENDING; the worker must come back.
            store.publishNow("a")
            runCurrent()
            assertEquals(listOf(true, false, true), decisions)
        }
}
