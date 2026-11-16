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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The **one** vocabulary the merged "Agent work" board speaks, folding two protocols the user should
 * never have to tell apart: agent **jobs** (kinds 43001-43006, no gate) and **workflow runs** (46020
 * + the 46010 human-approval gate). Both are "ask the agent → get a PR"; the only real difference is
 * whether a human must approve before it ships — so here that difference is a card **state**
 * ([NEEDS_APPROVAL]), not a separate screen.
 */
enum class AgentWorkState {
    /** Parked on a workflow approval gate — a named human must grant/deny before it ships. Sorts first. */
    NEEDS_APPROVAL,

    /** The agent is actively working (job accepted/in-progress, or a triggered/approved run). */
    WORKING,

    /** Filed but not picked up yet. */
    QUEUED,

    /** Terminal success — its result is a PR. */
    SHIPPED,

    /** Terminal not-success — failed, cancelled, or denied. */
    CLOSED,
}

/** Which protocol an item came from — surfaced only as a quiet tag, never as a navigation choice. */
enum class AgentWorkKind {
    /** Ungated agent job (43001-43006): ships its PR directly. */
    JOB,

    /** Gated workflow run (46020 + 46010): pauses for a human before shipping. */
    WORKFLOW,
}

/** One unit of agent work on the merged board, projected from either a [JobView] or a [WorkflowRun]. */
@Immutable
data class AgentWorkItem(
    val id: HexKey,
    val source: AgentWorkKind,
    val title: String,
    val requester: HexKey?,
    val state: AgentWorkState,
    /** When [NEEDS_APPROVAL], the key the gate asked to decide. */
    val pendingApprover: HexKey?,
    /** A shipped item's result — typically the PR URL. */
    val result: String?,
    /** The most recent progress/step line, or the error/close reason. */
    val detail: String?,
    /** Upvote count for jobs (the group's priority signal); null for workflow runs. */
    val upvotes: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Projects the two aggregators' outputs into one board, needs-approval first. */
object AgentWorkBoard {
    fun from(run: WorkflowRun): AgentWorkItem =
        AgentWorkItem(
            id = run.runId,
            source = AgentWorkKind.WORKFLOW,
            title = run.task?.takeIf { it.isNotBlank() } ?: run.workflowId?.let { "Workflow $it" } ?: "(no description)",
            requester = run.requester,
            state =
                when (run.state) {
                    WorkflowRunState.AWAITING_APPROVAL -> AgentWorkState.NEEDS_APPROVAL
                    WorkflowRunState.RUNNING, WorkflowRunState.APPROVED -> AgentWorkState.WORKING
                    WorkflowRunState.TRIGGERED -> AgentWorkState.QUEUED
                    WorkflowRunState.COMPLETED -> AgentWorkState.SHIPPED
                    WorkflowRunState.FAILED, WorkflowRunState.CANCELLED, WorkflowRunState.DENIED -> AgentWorkState.CLOSED
                },
            pendingApprover = run.pendingApprover,
            result = run.result,
            detail = run.lastStep ?: run.error,
            upvotes = null,
            createdAt = run.createdAt,
            updatedAt = run.updatedAt,
        )

    fun from(job: JobView): AgentWorkItem =
        AgentWorkItem(
            id = job.jobId,
            source = AgentWorkKind.JOB,
            title = job.request?.takeIf { it.isNotBlank() } ?: "(no description)",
            requester = job.requester,
            state =
                when (job.state) {
                    JobState.REQUESTED -> AgentWorkState.QUEUED
                    JobState.ACCEPTED, JobState.IN_PROGRESS -> AgentWorkState.WORKING
                    JobState.COMPLETED -> AgentWorkState.SHIPPED
                    JobState.FAILED, JobState.CANCELLED -> AgentWorkState.CLOSED
                },
            pendingApprover = null,
            result = job.result,
            detail = job.lastProgress ?: job.error ?: job.cancelReason,
            upvotes = job.upvotes,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
        )

    /**
     * Merge + order: needs-approval first (it blocks the room), then working, then the queue
     * (highest-upvoted first), then shipped, then closed — recency breaks ties within each band.
     */
    fun merge(
        runs: List<WorkflowRun>,
        jobs: List<JobView>,
    ): List<AgentWorkItem> =
        (runs.map { from(it) } + jobs.map { from(it) })
            .sortedWith(
                compareBy<AgentWorkItem> { it.state.ordinal }
                    // Upvotes are the queue's priority signal only. Elsewhere they would put an old
                    // upvoted job above a fresh one, and every job above every workflow run.
                    .thenByDescending { if (it.state == AgentWorkState.QUEUED) it.upvotes ?: 0 else 0 }
                    .thenByDescending { it.updatedAt },
            )
}
