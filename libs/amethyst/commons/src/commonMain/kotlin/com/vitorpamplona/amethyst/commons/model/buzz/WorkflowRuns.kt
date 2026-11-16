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
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.buzz.workflow.workflowChannel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Buzz workflow run/step lifecycle events (46001-46012) carry only a channel `h` tag and a
 * free-form JSON `content` (they're relay-emitted and their content schema isn't pinned by Buzz).
 * This is the shape the Amethyst runner emits and the aggregator reads. The **run id is the
 * trigger event id**, and it doubles as the approval token — so an [ApprovalGrantEvent]'s `d`
 * tag equals the run id, and no separate token bookkeeping is needed.
 */
@Serializable
data class WorkflowRunPayload(
    val run: String? = null,
    val workflow: String? = null,
    val task: String? = null,
    val step: String? = null,
    val pr: String? = null,
    val error: String? = null,
    val note: String? = null,
)

/** The lifecycle state of one workflow run. */
enum class WorkflowRunState {
    TRIGGERED,
    RUNNING,

    /** Paused on a 46010 approval gate — a human must grant or deny before it proceeds. */
    AWAITING_APPROVAL,
    APPROVED,
    COMPLETED,
    FAILED,

    /** The relay/runner cancelled the run (46007). */
    CANCELLED,

    /** A human denied the approval gate (46031). */
    DENIED,
}

/** One workflow run, folded from its trigger + lifecycle + approval events. */
@Immutable
data class WorkflowRun(
    val runId: HexKey,
    val workflowId: String?,
    val channel: String?,
    val task: String?,
    /** Who triggered the run (the 46020 author). */
    val requester: HexKey?,
    val state: WorkflowRunState,
    /** When [AWAITING_APPROVAL], the key the 46010 asked to decide (its `p` tag). */
    val pendingApprover: HexKey?,
    /** The result payload of a completed run — e.g. the PR URL. */
    val result: String?,
    val error: String?,
    val lastStep: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isTerminal: Boolean
        get() =
            state == WorkflowRunState.COMPLETED || state == WorkflowRunState.FAILED ||
                state == WorkflowRunState.CANCELLED || state == WorkflowRunState.DENIED

    /** The approval token to grant/deny this run is the run id itself. */
    val approvalToken: HexKey get() = runId
}

/**
 * Folds Buzz workflow events into per-run [WorkflowRun] records. Correlates by run id: the trigger
 * (46020) *is* the run (id = run id); every lifecycle event (46001-46010) carries `run` in its JSON
 * content; an approval grant/deny (46030/46031) references the run via its `d` tag (= the token =
 * the run id). Pure and platform-agnostic — shared by the `amy` runner/CLI and the mobile app.
 *
 * This is Buzz's **source-confirmed** structured-work + human-approval primitive (the command
 * events 30620/46020/46030/46031 are pinned against buzz-relay's Rust handlers); the lifecycle
 * content shape is Amethyst's, since Buzz leaves it relay-defined. See
 * `cli/plans/2026-07-25-buzz-agent-support-channel.md`.
 */
object WorkflowRunAggregator {
    private val json = Json { ignoreUnknownKeys = true }

    /** The run/step JSON of a workflow event, or null when the content is not that object. */
    fun payload(content: String): WorkflowRunPayload? = runCatching { json.decodeFromString<WorkflowRunPayload>(content) }.getOrNull()

    fun aggregate(events: List<Event>): List<WorkflowRun> {
        if (events.isEmpty()) return emptyList()
        val distinct = events.distinctBy { it.id }

        // run id -> events that belong to it.
        val byRun = LinkedHashMap<HexKey, MutableList<Event>>()

        // Approval decisions reference the run via the `d` tag (= run id = token).
        val grants = HashMap<HexKey, Long>() // runId -> newest grant time
        val denies = HashMap<HexKey, Long>()

        distinct.forEach { e ->
            when (e) {
                is WorkflowTriggerEvent -> byRun.getOrPut(e.id) { mutableListOf() }.add(e)
                is WorkflowTriggeredEvent -> route(e, byRun)
                is WorkflowStepStartedEvent -> route(e, byRun)
                is WorkflowStepCompletedEvent -> route(e, byRun)
                is WorkflowApprovalRequestedEvent -> route(e, byRun)
                is WorkflowCompletedEvent -> route(e, byRun)
                is WorkflowFailedEvent -> route(e, byRun)
                is WorkflowCancelledEvent -> route(e, byRun)
                is ApprovalGrantEvent -> e.tokenHash()?.let { grants[it] = maxOf(grants[it] ?: 0, e.createdAt) }
                is ApprovalDenyEvent -> e.tokenHash()?.let { denies[it] = maxOf(denies[it] ?: 0, e.createdAt) }
                else -> Unit
            }
        }

        return byRun
            .map { (runId, thread) -> fold(runId, thread, grants[runId], denies[runId]) }
            .sortedByDescending { it.updatedAt }
    }

    /** Group a lifecycle event under the run id carried in its JSON `content`. */
    private fun route(
        e: Event,
        byRun: MutableMap<HexKey, MutableList<Event>>,
    ) {
        val run = payload(e.content)?.run ?: return
        byRun.getOrPut(run) { mutableListOf() }.add(e)
    }

    private fun fold(
        runId: HexKey,
        thread: List<Event>,
        grantAt: Long?,
        denyAt: Long?,
    ): WorkflowRun {
        val trigger = thread.filterIsInstance<WorkflowTriggerEvent>().maxByOrNull { it.createdAt }
        val triggered = thread.filterIsInstance<WorkflowTriggeredEvent>().maxByOrNull { it.createdAt }
        val approval = thread.filterIsInstance<WorkflowApprovalRequestedEvent>().maxByOrNull { it.createdAt }
        val completed = thread.filterIsInstance<WorkflowCompletedEvent>().maxByOrNull { it.createdAt }
        val failed = thread.filterIsInstance<WorkflowFailedEvent>().maxByOrNull { it.createdAt }
        val cancelled = thread.filterIsInstance<WorkflowCancelledEvent>().maxByOrNull { it.createdAt }
        val steps =
            (thread.filterIsInstance<WorkflowStepStartedEvent>() + thread.filterIsInstance<WorkflowStepCompletedEvent>())
                .sortedBy { it.createdAt }

        // Parse each event's JSON content at most once — `fold` runs per run, per board re-derive.
        val triggerPayload = trigger?.let { payload(it.content) }
        val triggeredPayload = triggered?.let { payload(it.content) }

        // Terminal outcomes and the deny decision compete by timestamp — newest wins.
        val terminal =
            listOfNotNull(
                completed?.let { WorkflowRunState.COMPLETED to it.createdAt },
                failed?.let { WorkflowRunState.FAILED to it.createdAt },
                cancelled?.let { WorkflowRunState.CANCELLED to it.createdAt },
                denyAt?.let { WorkflowRunState.DENIED to it },
            ).maxByOrNull { it.second }?.first

        val state =
            terminal
                ?: when {
                    // An approval that hasn't been granted yet holds the run at the gate.
                    approval != null && (grantAt == null || approval.createdAt > grantAt) -> WorkflowRunState.AWAITING_APPROVAL
                    grantAt != null -> WorkflowRunState.APPROVED
                    steps.isNotEmpty() || triggered != null -> WorkflowRunState.RUNNING
                    else -> WorkflowRunState.TRIGGERED
                }

        return WorkflowRun(
            runId = runId,
            workflowId = trigger?.workflowId() ?: triggerPayload?.workflow ?: triggeredPayload?.workflow,
            channel = trigger?.tags?.workflowChannel() ?: approval?.channel() ?: triggered?.channel(),
            task = triggerPayload?.task ?: triggeredPayload?.task,
            requester = trigger?.pubKey,
            state = state,
            pendingApprover = approval?.approver(),
            result = completed?.let { payload(it.content)?.pr },
            error = failed?.let { payload(it.content)?.error },
            lastStep = steps.lastOrNull()?.let { payload(it.content)?.step },
            createdAt = trigger?.createdAt ?: thread.minOf { it.createdAt },
            updatedAt = maxOf(thread.maxOf { it.createdAt }, grantAt ?: 0, denyAt ?: 0),
        )
    }

    /** Backlog ordering: awaiting-approval first (needs a human), then running, then the rest by recency. */
    fun byPriority(runs: List<WorkflowRun>): List<WorkflowRun> =
        runs.sortedWith(
            compareByDescending<WorkflowRun> { it.state == WorkflowRunState.AWAITING_APPROVAL }
                .thenByDescending { it.updatedAt },
        )
}
