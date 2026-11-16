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

import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiRollup
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiState
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiStatusIndex
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.CiPullRequestContext
import com.vitorpamplona.quartz.nip34Git.ci.CiRunContext
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowFile
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CiRollupTest {
    private val sig = "0".repeat(128)
    private val pr = "a".repeat(64)
    private val otherPr = "b".repeat(64)
    private val coordinator = "c".repeat(64)
    private val otherCoordinator = "d".repeat(64)
    private val owner = "e".repeat(64)

    private var nextId = 0

    private fun id() = (++nextId).toString(16).padStart(64, '0')

    private fun context(
        workflow: String,
        item: String = pr,
    ) = CiRunContext(
        repositories = listOf(ATag(30617, owner, "repo", null)),
        commits = listOf("1".repeat(40)),
        workflow = CiWorkflowFile(workflow, "f".repeat(64)),
        pullRequest = CiPullRequestContext(pullRequestId = item, pullRequestAuthor = owner),
    )

    private fun result(
        workflow: String,
        conclusion: CiConclusion,
        createdAt: Long,
        runId: String = id(),
        signer: String = coordinator,
        startedAt: Long? = null,
        item: String = pr,
        jobs: List<CiJobResultQuote> = emptyList(),
    ): CiWorkflowResultEvent {
        val template = CiWorkflowResultEvent.build(context(workflow, item), CiTrigger.PULL_REQUEST, runId, conclusion, jobs, startedAt = startedAt, createdAt = createdAt)
        return CiWorkflowResultEvent(id(), signer, createdAt, template.tags, template.content, sig)
    }

    private fun progress(
        workflow: String,
        status: CiWorkflowStatus,
        createdAt: Long,
        runId: String,
        conclusion: CiConclusion? = null,
        queuedAt: Long? = null,
        expiresAt: Long = createdAt + 600,
        signer: String = coordinator,
    ): CiWorkflowProgressEvent {
        val template = CiWorkflowProgressEvent.build(runId, context(workflow), CiTrigger.PULL_REQUEST, status, conclusion, queuedAt = queuedAt, createdAt = createdAt, expiresAt = expiresAt)
        return CiWorkflowProgressEvent(id(), signer, createdAt, template.tags, template.content, sig)
    }

    @Test
    fun nothingRanIsNoBadge() {
        val summary = CiRollup.summarize(emptyList(), emptyList(), now = 1000)
        assertNull(summary.state)
        assertTrue(summary.workflows.isEmpty())
    }

    @Test
    fun latestAttemptPerWorkflowAndCoordinatorIsCurrent() {
        val failed = result("ci.yml", CiConclusion.FAILURE, createdAt = 100)
        val rerun = result("ci.yml", CiConclusion.SUCCESS, createdAt = 200)

        val summary = CiRollup.summarize(listOf(rerun, failed), emptyList(), now = 1000)

        assertEquals(CiState.SUCCESS, summary.state)
        val runs = summary.workflows.single()
        // Each 9842 stays its own attempt: the older failure is history, not merged away.
        assertEquals(2, runs.attempts.size)
        assertSame(rerun, runs.current.result)
        assertSame(failed, runs.attempts[1].result)
    }

    @Test
    fun startedAtOrdersAttemptsBeforeCreatedAt() {
        val startedLater = result("ci.yml", CiConclusion.FAILURE, createdAt = 100, startedAt = 90)
        val startedEarlier = result("ci.yml", CiConclusion.SUCCESS, createdAt = 300, startedAt = 50)

        val summary = CiRollup.summarize(listOf(startedEarlier, startedLater), emptyList(), now = 1000)

        assertEquals(CiState.FAILURE, summary.state)
    }

    @Test
    fun differentCoordinatorsAreSeparateWorkflowsAndAnyFailureFails() {
        val ours = result("ci.yml", CiConclusion.SUCCESS, createdAt = 100)
        val theirs = result("ci.yml", CiConclusion.TIMED_OUT, createdAt = 50, signer = otherCoordinator)

        val summary = CiRollup.summarize(listOf(ours, theirs), emptyList(), now = 1000)

        assertEquals(2, summary.workflows.size)
        assertEquals(CiState.FAILURE, summary.state)
        assertEquals(1, summary.passed)
    }

    @Test
    fun liveQueuedOrRunningMarkerIsPending() {
        val passed = result("lint.yml", CiConclusion.SUCCESS, createdAt = 100)
        val running = progress("test.yml", CiWorkflowStatus.IN_PROGRESS, createdAt = 500, runId = "run-1")

        val summary = CiRollup.summarize(listOf(passed), listOf(running), now = 600)

        assertEquals(CiState.PENDING, summary.state)
        assertEquals(1100L, summary.nextExpiration)
    }

    @Test
    fun expiredMarkerIsIgnored() {
        val running = progress("test.yml", CiWorkflowStatus.QUEUED, createdAt = 500, runId = "run-1", expiresAt = 900)

        assertEquals(CiState.PENDING, CiRollup.summarize(emptyList(), listOf(running), now = 899).state)
        assertNull(CiRollup.summarize(emptyList(), listOf(running), now = 900).state)
    }

    @Test
    fun markerWhoseRunHasAResultIsSuperseded() {
        val running = progress("test.yml", CiWorkflowStatus.IN_PROGRESS, createdAt = 500, runId = "run-1")
        val done = result("test.yml", CiConclusion.FAILURE, createdAt = 550, runId = "run-1")

        val summary = CiRollup.summarize(listOf(done), listOf(running), now = 600)

        assertEquals(CiState.FAILURE, summary.state)
        assertEquals(
            1,
            summary.workflows
                .single()
                .attempts.size,
        )
        assertNull(summary.nextExpiration)
    }

    @Test
    fun newerMarkerForTheSameRunReplacesTheOlder() {
        val running = progress("test.yml", CiWorkflowStatus.IN_PROGRESS, createdAt = 500, runId = "run-1")
        val concluded = progress("test.yml", CiWorkflowStatus.CONCLUDED, createdAt = 520, runId = "run-1", conclusion = CiConclusion.SUCCESS)

        val summary = CiRollup.summarize(emptyList(), listOf(running, concluded), now = 600)

        assertEquals(CiState.SUCCESS, summary.state)
        assertEquals(
            1,
            summary.workflows
                .single()
                .attempts.size,
        )
    }

    @Test
    fun aRerunQueuedAfterAResultIsTheCurrentAttempt() {
        val failed = result("test.yml", CiConclusion.FAILURE, createdAt = 400)
        // A renewed marker: its created_at moves, its queued_at does not.
        val rerun = progress("test.yml", CiWorkflowStatus.QUEUED, createdAt = 590, runId = "run-2", queuedAt = 450)

        val summary = CiRollup.summarize(listOf(failed), listOf(rerun), now = 600)

        assertEquals(CiState.PENDING, summary.state)
        assertSame(
            rerun,
            summary.workflows
                .single()
                .current.progress,
        )
    }

    @Test
    fun conclusionsMapToBadgeStates() {
        assertEquals(CiState.FAILURE, CiRollup.stateOf(CiConclusion.STARTUP_FAILURE))
        assertEquals(CiState.CANCELLED, CiRollup.stateOf(CiConclusion.CANCELLED))
        assertEquals(CiState.NEUTRAL, CiRollup.stateOf(CiConclusion.SKIPPED))
        assertEquals(CiState.NEUTRAL, CiRollup.stateOf(null))
        assertEquals(CiState.CANCELLED, CiRollup.combine(listOf(CiState.SUCCESS, CiState.CANCELLED)))
        assertEquals(CiState.NEUTRAL, CiRollup.combine(listOf(CiState.NEUTRAL)))
        assertEquals(CiState.PENDING, CiRollup.combine(listOf(CiState.SUCCESS, CiState.PENDING, CiState.CANCELLED)))
    }

    @Test
    fun indexGroupsRunsByTheItemTheyRanFor() {
        val one = result("ci.yml", CiConclusion.SUCCESS, createdAt = 100)
        val two = result("ci.yml", CiConclusion.FAILURE, createdAt = 100, item = otherPr)
        val marker = progress("ci.yml", CiWorkflowStatus.QUEUED, createdAt = 100, runId = "run-1")

        val index = CiStatusIndex.groupByItem(listOf(one, two, marker))

        assertEquals(listOf(one), index[pr]?.results)
        assertEquals(listOf(marker), index[pr]?.progress)
        assertEquals(listOf(two), index[otherPr]?.results)
    }

    @Test
    fun attemptsExposeOnlyTheJobsTheyQuote() {
        val quote = CiJobResultQuote("9".repeat(64), null, otherCoordinator, "build")
        val done = result("ci.yml", CiConclusion.SUCCESS, createdAt = 100, jobs = listOf(quote))

        val attempt =
            CiRollup
                .summarize(listOf(done), emptyList(), now = 1000)
                .workflows
                .single()
                .current

        assertEquals(listOf(quote), attempt.jobs)
        assertEquals("ci.yml", attempt.workflowPath)
        assertEquals(coordinator, attempt.coordinator)
    }
}
