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
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

/** The lifecycle state of one Buzz agent job, folded from its 43001-43006 events. */
enum class JobState {
    /** Only the 43001 request is present — no agent has picked it up yet. */
    REQUESTED,

    /** An agent published a 43002 acceptance. */
    ACCEPTED,

    /** At least one 43003 progress ping has arrived after acceptance. */
    IN_PROGRESS,

    /** Terminal success — a 43004 result. */
    COMPLETED,

    /** Terminal failure — a 43006 error. */
    FAILED,

    /** Terminal cancellation — a 43005 cancel. */
    CANCELLED,
}

/**
 * One agent job, correlated from its request and every reply that references it.
 *
 * A job is identified by the id of its [JobRequestEvent] (kind 43001). Every reply
 * (accepted/progress/result/error/cancel) points back to that id via its `e` tag, so
 * the whole thread folds into a single record regardless of how many relays served it.
 */
@Immutable
data class JobView(
    val jobId: HexKey,
    /** The request author — who asked for the work (or a `p` counterparty when the request event is absent). */
    val requester: HexKey?,
    /** The target/responding agent — the request `p` tag, else whoever signed the agent-side replies. */
    val agent: HexKey?,
    /** The `h` channel UUID the job is scoped to, if any. */
    val channel: String?,
    /** The task description (43001 content); null when the request event wasn't in the input set. */
    val request: String?,
    val state: JobState,
    /** The most recent 43003 progress message, if any. */
    val lastProgress: String?,
    val progressUpdates: Int,
    /** The 43004 result payload, when [state] is COMPLETED. */
    val result: String?,
    /** The 43006 error message, when [state] is FAILED. */
    val error: String?,
    /** The 43005 cancel reason, when [state] is CANCELLED. */
    val cancelReason: String?,
    /**
     * Distinct channel members who upvoted this job — a NIP-25 like (kind-7, content not `-`)
     * whose `e` tag targets the job's request id. The group's priority signal: a scheduler
     * orders the backlog by this, newest-first as a tiebreaker.
     */
    val upvotes: Int,
    /** The request timestamp, or the earliest correlated event when the request is absent. */
    val createdAt: Long,
    /** The timestamp of the most recent event in the thread. */
    val updatedAt: Long,
) {
    val isTerminal: Boolean get() = state == JobState.COMPLETED || state == JobState.FAILED || state == JobState.CANCELLED
}

/**
 * Folds a flat list of Buzz agent-job events (kinds 43001-43006, as dispatched by
 * `EventFactory`) into per-job [JobView] records. Pure and platform-agnostic so both the
 * `amy` CLI and a future mobile Jobs board share one correlation + state machine.
 *
 * SCHEMA CAVEAT: the 43001-43006 protocol is *reserved* in Buzz (no upstream builder);
 * the tag layout this reads (`e` request reference, `h` channel, `p` counterparty,
 * `status` token) is Quartz's best-effort model and must be reconciled once Buzz
 * implements the job protocol. See [JobRequestEvent].
 */
object BuzzJobAggregator {
    fun aggregate(events: List<Event>): List<JobView> {
        if (events.isEmpty()) return emptyList()

        val distinct = events.distinctBy { it.id }

        // Upvotes: distinct authors of a NIP-25 like (kind-7, content not `-`) per targeted
        // event id. Counting distinct pubkeys stops one member inflating priority by spamming.
        val upvoters = HashMap<HexKey, MutableSet<HexKey>>()
        distinct.forEach { e ->
            if (e is ReactionEvent && e.content != ReactionEvent.DISLIKE) {
                e.originalPost().forEach { target -> upvoters.getOrPut(target) { mutableSetOf() }.add(e.pubKey) }
            }
        }

        // Correlate every job event to a job id: the request is its own id; a reply carries
        // the request id in its `e` tag. Replies we can't correlate (no `e`) are dropped.
        val byJob = LinkedHashMap<HexKey, MutableList<Event>>()
        distinct.forEach { e ->
            val jobId =
                when (e) {
                    is JobRequestEvent -> e.id
                    is JobAcceptedEvent -> e.jobRequest()
                    is JobProgressEvent -> e.jobRequest()
                    is JobResultEvent -> e.jobRequest()
                    is JobErrorEvent -> e.jobRequest()
                    is JobCancelEvent -> e.jobRequest()
                    else -> null
                } ?: return@forEach
            byJob.getOrPut(jobId) { mutableListOf() }.add(e)
        }

        return byJob
            .map { (jobId, thread) -> fold(jobId, thread, upvoters[jobId]?.size ?: 0) }
            .sortedByDescending { it.updatedAt }
    }

    private fun fold(
        jobId: HexKey,
        thread: List<Event>,
        upvotes: Int,
    ): JobView {
        val request = thread.filterIsInstance<JobRequestEvent>().maxByOrNull { it.createdAt }
        val accepted = thread.filterIsInstance<JobAcceptedEvent>().maxByOrNull { it.createdAt }
        val progress = thread.filterIsInstance<JobProgressEvent>().sortedBy { it.createdAt }
        val result = thread.filterIsInstance<JobResultEvent>().maxByOrNull { it.createdAt }
        val error = thread.filterIsInstance<JobErrorEvent>().maxByOrNull { it.createdAt }
        val cancel = thread.filterIsInstance<JobCancelEvent>().maxByOrNull { it.createdAt }

        // Among terminal outcomes, the newest one wins (a result can supersede a cancel and
        // vice-versa depending on arrival order — trust the latest signed timestamp).
        val terminal =
            listOfNotNull(
                result?.let { JobState.COMPLETED to it.createdAt },
                error?.let { JobState.FAILED to it.createdAt },
                cancel?.let { JobState.CANCELLED to it.createdAt },
            ).maxByOrNull { it.second }?.first

        val state =
            terminal
                ?: when {
                    progress.isNotEmpty() -> JobState.IN_PROGRESS
                    accepted != null -> JobState.ACCEPTED
                    else -> JobState.REQUESTED
                }

        val requester = request?.pubKey ?: cancel?.pubKey ?: accepted?.requester() ?: result?.requester()
        val agent = request?.target() ?: accepted?.pubKey ?: result?.pubKey ?: error?.pubKey ?: progress.lastOrNull()?.pubKey

        return JobView(
            jobId = jobId,
            requester = requester,
            agent = agent,
            channel = request?.channel() ?: accepted?.channel() ?: result?.channel(),
            request = request?.request(),
            state = state,
            lastProgress = progress.lastOrNull()?.content,
            progressUpdates = progress.size,
            result = result?.result(),
            error = error?.error(),
            cancelReason = cancel?.reason()?.ifBlank { null },
            upvotes = upvotes,
            createdAt = request?.createdAt ?: thread.minOf { it.createdAt },
            updatedAt = thread.maxOf { it.createdAt },
        )
    }

    /**
     * Backlog ordering for a scheduler: most-upvoted first (the group's priority signal),
     * oldest-first as the tiebreaker so an un-upvoted item still drains FIFO. Applies to the
     * caller-provided set (typically the REQUESTED jobs targeting the agent).
     */
    fun byPriority(jobs: List<JobView>): List<JobView> = jobs.sortedWith(compareByDescending<JobView> { it.upvotes }.thenBy { it.createdAt })
}
