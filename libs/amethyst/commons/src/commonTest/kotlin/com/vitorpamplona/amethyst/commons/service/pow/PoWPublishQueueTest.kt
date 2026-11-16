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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PoWPublishQueueTest {
    val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    val template =
        EventTemplate<TextNoteEvent>(
            1683596206,
            TextNoteEvent.KIND,
            emptyArray(),
            "A note to mine",
        )

    @Test
    fun minesTemplateThenRunsContinuation() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)
            val mined = CompletableDeferred<EventTemplate<TextNoteEvent>>()

            queue.enqueue(template, pubKey, difficulty = 10) { mined.complete(it) }

            val result = withContext(Dispatchers.Default) { withTimeout(60_000) { mined.await() } }

            val powTag = result.tags.firstNotNullOfOrNull { PoWTag.parse(it) }
            assertNotNull(powTag, "mined template must carry a nonce tag")
            assertEquals(10, powTag.commitment)

            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }

    @Test
    fun cancellingAQueuedJobSkipsItsWork() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val gate = CompletableDeferred<Unit>()
            var secondRan = false

            // occupies the single worker until the gate opens
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueueWork(kind = 1, difficulty = 10) { secondRan = true }

            val jobs = queue.jobs.value
            assertEquals(2, jobs.size)
            assertTrue(jobs.map { it.kind }.all { it == 1 })

            queue.cancel(jobs[1].id)
            assertEquals(1, queue.jobs.value.size, "cancelled job leaves the visible queue immediately")

            gate.complete(Unit)

            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertFalse(secondRan, "cancelled job must never run")
            scope.cancel()
        }

    @Test
    fun sendWithoutPowPublishesTheUnminedTemplate() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)
            val sent = CompletableDeferred<EventTemplate<TextNoteEvent>>()

            // difficulty 40 would mine for ages: it must NOT finish before we
            // abandon it, so what publishes is guaranteed the un-mined template.
            queue.enqueue(template, pubKey, difficulty = 40) { sent.complete(it) }

            // wait until a worker is actually searching, then send now.
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.any { j -> j.isMining } } } }
            val jobId =
                queue.jobs.value
                    .first()
                    .id
            assertTrue(
                queue.jobs.value
                    .first()
                    .canSendWithoutPow,
                "template jobs offer the un-mined fallback",
            )

            queue.sendWithoutPow(jobId)

            val result = withContext(Dispatchers.Default) { withTimeout(10_000) { sent.await() } }
            val powTag = result.tags.firstNotNullOfOrNull { PoWTag.parse(it) }
            assertEquals(null, powTag, "an un-mined send must carry no nonce tag")

            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }

    @Test
    fun sendWithoutPowIsNoOpForOpaqueWorkJobs() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val gate = CompletableDeferred<Unit>()
            var ran = false
            // occupies the worker so the second job stays QUEUED and observable
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueueWork(kind = 1, difficulty = 10) { ran = true }

            val workJob = queue.jobs.value[1]
            assertFalse(workJob.canSendWithoutPow, "opaque work jobs have no un-mined fallback")

            // no fallback → the request is ignored, the job keeps its place.
            queue.sendWithoutPow(workJob.id)
            assertEquals(2, queue.jobs.value.size)

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertTrue(ran, "the work job still runs its normal mining path")
            scope.cancel()
        }

    @Test
    fun jobsRunInFifoOrder() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val order = mutableListOf<Int>()
            val done = CompletableDeferred<Unit>()

            repeat(3) { index ->
                queue.enqueueWork(kind = 1, difficulty = 10) {
                    order.add(index)
                    if (index == 2) done.complete(Unit)
                }
            }

            withContext(Dispatchers.Default) { withTimeout(10_000) { done.await() } }
            assertEquals(listOf(0, 1, 2), order)
            scope.cancel()
        }

    private class FakePersistence : PoWJobPersistence {
        val saved = mutableListOf<String>()
        val removed = mutableListOf<String>()

        override fun save(job: PersistedPoWJob) {
            saved.add(job.id)
        }

        override fun remove(jobId: String) {
            removed.add(jobId)
        }
    }

    private fun recordFor(id: String) =
        PersistedPoWJob(
            id = id,
            accountPubkey = pubKey,
            kind = TextNoteEvent.KIND,
            difficulty = 10,
            templateJson = template.toJson(),
            replayType = PersistedPoWJob.REPLAY_BROADCAST,
        )

    @Test
    fun persistedJobIsSavedThenRemovedOnCompletion() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)
            val mined = CompletableDeferred<Unit>()

            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-a")) { mined.complete(Unit) }
            assertEquals(listOf("job-a"), persistence.saved)

            withContext(Dispatchers.Default) { withTimeout(60_000) { mined.await() } }
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertEquals(listOf("job-a"), persistence.removed)
            scope.cancel()
        }

    @Test
    fun cancellingAPersistedJobRemovesItsCheckpoint() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)

            val gate = CompletableDeferred<Unit>()
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-b")) {}

            queue.cancel("job-b")
            assertTrue("job-b" in persistence.removed, "cancel must drop the checkpoint")

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }

    @Test
    fun refreshCreatedAtReStampsAtMiningStart() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)
            val mined = CompletableDeferred<EventTemplate<TextNoteEvent>>()

            // template stamped in 2023; refresh must bring it to "now"
            queue.enqueue(template, pubKey, difficulty = 10, refreshCreatedAt = true) { mined.complete(it) }

            val result = withContext(Dispatchers.Default) { withTimeout(60_000) { mined.await() } }
            assertTrue(
                result.createdAt > template.createdAt,
                "created_at must be re-stamped at mining start (was ${result.createdAt})",
            )
            scope.cancel()
        }

    @Test
    fun duplicateJobIdsAreEnqueuedOnce() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)

            val gate = CompletableDeferred<Unit>()
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }

            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-c")) {}
            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-c")) {}

            assertEquals(2, queue.jobs.value.size, "restore-style re-enqueue of the same id must not duplicate")
            assertEquals(listOf("job-c"), persistence.saved)

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(60_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }

    @Test
    fun failedPublishKeepsCheckpointAndReportsFailure() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)

            val failure = CompletableDeferred<PoWJobFailure>()
            scope.launch { queue.failures.collect { failure.complete(it) } }
            // give the collector a beat to subscribe (failures has no replay)
            withContext(Dispatchers.Default) { delay(50) }

            queue.enqueueStaged(
                kind = TextNoteEvent.KIND,
                difficulty = 10,
                persistAs = recordFor("job-d"),
                mine = { "nonce" },
                publish = { throw IllegalStateException("signer rejected") },
            )

            val reported = withContext(Dispatchers.Default) { withTimeout(10_000) { failure.await() } }
            assertEquals(TextNoteEvent.KIND, reported.kind)
            assertTrue(reported.willRetryOnRestart, "persisted job must be retried by the restorer")

            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertFalse("job-d" in persistence.removed, "checkpoint must survive a failed publish for restart retry")
            scope.cancel()
        }

    @Test
    fun aQueueWithoutPersistenceNeverPromisesARetryOnRestart() =
        runTest {
            // A desktop copy that is not the primary one mines without checkpoints.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = null)

            val failure = CompletableDeferred<PoWJobFailure>()
            scope.launch { queue.failures.collect { failure.complete(it) } }
            withContext(Dispatchers.Default) { delay(50) }

            queue.enqueueStaged(
                kind = TextNoteEvent.KIND,
                difficulty = 10,
                persistAs = recordFor("job-unsaved"),
                mine = { "nonce" },
                publish = { throw IllegalStateException("signer rejected") },
            )

            val reported = withContext(Dispatchers.Default) { withTimeout(10_000) { failure.await() } }
            assertFalse(reported.willRetryOnRestart, "nothing was saved, so nothing retries on restart")
            scope.cancel()
        }

    @Test
    fun checkpointSurvivesUntilPublishCompletes() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)

            val publishing = CompletableDeferred<Unit>()
            val publishGate = CompletableDeferred<Unit>()

            queue.enqueueStaged(
                kind = TextNoteEvent.KIND,
                difficulty = 10,
                persistAs = recordFor("job-e"),
                mine = { "nonce" },
                publish = {
                    publishing.complete(Unit)
                    publishGate.await()
                },
            )

            withContext(Dispatchers.Default) { withTimeout(10_000) { publishing.await() } }
            assertFalse("job-e" in persistence.removed, "checkpoint must not be dropped at mining-complete")

            val job = queue.jobs.value.first()
            assertEquals(PoWJobPhase.PUBLISHING, job.phase)
            assertFalse(job.isCancellable)

            // cancel is a no-op mid-publish: the event may be half-broadcast
            queue.cancel(job.id)
            assertEquals(1, queue.jobs.value.size)

            publishGate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertTrue("job-e" in persistence.removed, "checkpoint drops once publish completes")
            scope.cancel()
        }

    @Test
    fun cancelByKeyTogglesAPendingJob() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val gate = CompletableDeferred<Unit>()
            var reactionRan = false
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueueWork(kind = 7, difficulty = 10, dedupeKey = "reaction:abc:+") { reactionRan = true }

            // same key while pending → deduped
            queue.enqueueWork(kind = 7, difficulty = 10, dedupeKey = "reaction:abc:+") { reactionRan = true }
            assertEquals(2, queue.jobs.value.size)

            assertTrue(queue.cancelByKey("reaction:abc:+"), "first toggle cancels the pending like")
            assertFalse(queue.cancelByKey("reaction:abc:+"), "nothing left to cancel")

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertFalse(reactionRan, "cancelled reaction must never publish")
            scope.cancel()
        }

    @Test
    fun cancelForOwnerOnlyDropsThatAccountsJobs() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val gate = CompletableDeferred<Unit>()
            var otherRan = false
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueueWork(kind = 1, difficulty = 10, owner = "account-a") {}
            queue.enqueueWork(kind = 1, difficulty = 10, owner = "account-b") { otherRan = true }

            queue.cancelForOwner("account-a")
            assertEquals(2, queue.jobs.value.size, "only account-a's job leaves the queue")

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertTrue(otherRan, "account-b's job still runs")
            scope.cancel()
        }
}
