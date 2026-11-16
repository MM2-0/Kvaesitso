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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuzzJobAggregatorTest {
    private val requester = "a".repeat(64)
    private val agent = "f".repeat(64)
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val jobId = "1".repeat(64)

    // Events are built via the quartz template and constructed directly with an explicit
    // id/author/createdAt (no signing) — mirroring the quartz job-event unit tests. The
    // aggregator only reads typed accessors, so unsigned fixtures exercise it faithfully.
    private fun request(
        id: String = jobId,
        createdAt: Long = 1000,
    ): JobRequestEvent {
        val t = JobRequestEvent.build("fix the login bug", channel, agent, createdAt)
        return JobRequestEvent(id, requester, t.createdAt, t.tags, t.content, "sig")
    }

    private fun accepted(createdAt: Long = 1010): JobAcceptedEvent {
        val t = JobAcceptedEvent.build(jobId, channel, requester, "on it", createdAt)
        return JobAcceptedEvent("2".repeat(64), agent, t.createdAt, t.tags, t.content, "sig")
    }

    private fun progress(
        msg: String,
        createdAt: Long,
        id: String,
    ): JobProgressEvent {
        val t = JobProgressEvent.build(jobId, msg, channel, "running", createdAt)
        return JobProgressEvent(id, agent, t.createdAt, t.tags, t.content, "sig")
    }

    private fun result(createdAt: Long = 1040): JobResultEvent {
        val t = JobResultEvent.build(jobId, "opened PR #123", channel, requester, "completed", createdAt)
        return JobResultEvent("4".repeat(64), agent, t.createdAt, t.tags, t.content, "sig")
    }

    private fun error(createdAt: Long = 1040): JobErrorEvent {
        val t = JobErrorEvent.build(jobId, "build failed", channel, status = "error", createdAt = createdAt)
        return JobErrorEvent("6".repeat(64), agent, t.createdAt, t.tags, t.content, "sig")
    }

    private fun cancel(createdAt: Long = 1035): JobCancelEvent {
        val t = JobCancelEvent.build(jobId, "changed my mind", channel, createdAt)
        return JobCancelEvent("5".repeat(64), requester, t.createdAt, t.tags, t.content, "sig")
    }

    // A NIP-25 like targeting a job's request id — the group's upvote signal.
    private fun like(
        reactor: String,
        target: String = jobId,
        id: String,
        content: String = "+",
    ): ReactionEvent = ReactionEvent(id, reactor, 1050, arrayOf(arrayOf("e", target)), content, "sig")

    @Test
    fun emptyInput() {
        assertEquals(emptyList(), BuzzJobAggregator.aggregate(emptyList()))
    }

    @Test
    fun fullHappyPath() {
        val events: List<Event> =
            listOf(
                request(),
                accepted(),
                progress("cloning", 1020, "a1".padEnd(64, '0')),
                progress("running tests", 1030, "a2".padEnd(64, '0')),
                result(),
            )
        val jobs = BuzzJobAggregator.aggregate(events)
        assertEquals(1, jobs.size)
        val job = jobs.single()
        assertEquals(jobId, job.jobId)
        assertEquals(requester, job.requester)
        assertEquals(agent, job.agent)
        assertEquals(channel, job.channel)
        assertEquals("fix the login bug", job.request)
        assertEquals(JobState.COMPLETED, job.state)
        assertTrue(job.isTerminal)
        assertEquals("opened PR #123", job.result)
        assertEquals("running tests", job.lastProgress)
        assertEquals(2, job.progressUpdates)
        assertEquals(1000, job.createdAt)
        assertEquals(1040, job.updatedAt)
    }

    @Test
    fun requestOnlyIsRequested() {
        val job = BuzzJobAggregator.aggregate(listOf(request())).single()
        assertEquals(JobState.REQUESTED, job.state)
        assertTrue(!job.isTerminal)
        assertNull(job.result)
        assertEquals(0, job.progressUpdates)
    }

    @Test
    fun acceptedWithoutProgressIsAccepted() {
        val job = BuzzJobAggregator.aggregate(listOf(request(), accepted())).single()
        assertEquals(JobState.ACCEPTED, job.state)
    }

    @Test
    fun progressWithoutTerminalIsInProgress() {
        val job =
            BuzzJobAggregator
                .aggregate(listOf(request(), accepted(), progress("working", 1020, "a1".padEnd(64, '0'))))
                .single()
        assertEquals(JobState.IN_PROGRESS, job.state)
    }

    @Test
    fun errorIsFailed() {
        val job = BuzzJobAggregator.aggregate(listOf(request(), accepted(), error())).single()
        assertEquals(JobState.FAILED, job.state)
        assertEquals("build failed", job.error)
    }

    @Test
    fun cancelIsCancelled() {
        val job = BuzzJobAggregator.aggregate(listOf(request(), cancel())).single()
        assertEquals(JobState.CANCELLED, job.state)
        assertEquals("changed my mind", job.cancelReason)
    }

    @Test
    fun newestTerminalWins() {
        // A cancel at 1035 followed by a result at 1040: the later result is authoritative.
        val job = BuzzJobAggregator.aggregate(listOf(request(), cancel(1035), result(1040))).single()
        assertEquals(JobState.COMPLETED, job.state)

        // Reversed timing: a result at 1030 then a cancel at 1050 → CANCELLED.
        val job2 = BuzzJobAggregator.aggregate(listOf(request(), result(1030), cancel(1050))).single()
        assertEquals(JobState.CANCELLED, job2.state)
    }

    @Test
    fun orphanRepliesWithoutRequestStillFold() {
        // The 43001 request wasn't served to us, only the agent's result. We still surface
        // the job, keyed by the `e`-referenced request id, with the request text unknown.
        val job = BuzzJobAggregator.aggregate(listOf(accepted(), result())).single()
        assertEquals(jobId, job.jobId)
        assertNull(job.request)
        assertEquals(JobState.COMPLETED, job.state)
        assertEquals(agent, job.agent)
        // requester is recovered from the reply's `p` tag when the request event is absent.
        assertEquals(requester, job.requester)
    }

    @Test
    fun upvotesCountDistinctReactorsIgnoringDislikes() {
        val bob = "b".repeat(64)
        val carol = "c".repeat(64)
        val events =
            listOf(
                request(),
                like(bob, id = "e1".padEnd(64, '0')),
                like(carol, id = "e2".padEnd(64, '0')),
                like(bob, id = "e3".padEnd(64, '0')), // same reactor again → not double-counted
                like(carol, id = "e4".padEnd(64, '0'), content = "-"), // dislike → excluded
            )
        val job = BuzzJobAggregator.aggregate(events).single()
        assertEquals(2, job.upvotes)
    }

    @Test
    fun byPriorityOrdersByUpvotesThenAge() {
        val jobA = "a".repeat(64) // older, 0 upvotes
        val jobB = "b".repeat(64) // newer, 2 upvotes
        val jobC = "c".repeat(64) // oldest, 0 upvotes

        fun req(
            id: String,
            at: Long,
        ): JobRequestEvent {
            val t = JobRequestEvent.build("task $id", channel, agent, at)
            return JobRequestEvent(id, requester, t.createdAt, t.tags, t.content, "sig")
        }
        val events =
            listOf(
                req(jobA, 2000),
                req(jobB, 3000),
                req(jobC, 1000),
                like("1".repeat(64), target = jobB, id = "u1".padEnd(64, '0')),
                like("2".repeat(64), target = jobB, id = "u2".padEnd(64, '0')),
            )
        val ordered = BuzzJobAggregator.byPriority(BuzzJobAggregator.aggregate(events))
        // B first (2 upvotes), then the 0-upvote jobs oldest-first: C (1000) before A (2000).
        assertEquals(listOf(jobB, jobC, jobA), ordered.map { it.jobId })
    }

    @Test
    fun multipleJobsSortedByUpdatedAtDesc() {
        val otherId = "9".repeat(64)
        val otherReq = JobRequestEvent.build("second task", channel, agent, 2000)
        val other = JobRequestEvent(otherId, requester, otherReq.createdAt, otherReq.tags, otherReq.content, "sig")

        val jobs = BuzzJobAggregator.aggregate(listOf(request(), result(), other))
        assertEquals(2, jobs.size)
        // `request()`+`result()` updates at 1040; `other` at 2000 → other is newest, first.
        assertEquals(otherId, jobs.first().jobId)
    }
}
