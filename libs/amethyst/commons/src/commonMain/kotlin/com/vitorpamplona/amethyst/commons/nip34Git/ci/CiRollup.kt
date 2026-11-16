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
package com.vitorpamplona.amethyst.commons.nip34Git.ci

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent

/** What a CI badge shows: the state of one attempt, one workflow, or a whole PR / patch. */
enum class CiState {
    SUCCESS,
    FAILURE,
    PENDING,
    CANCELLED,
    NEUTRAL,
}

/**
 * One workflow run attempt, from either its immutable Workflow Result (9842) or — while no result
 * exists yet — its live Workflow Progress marker (39842). [event] is whichever of the two backs it;
 * its signer is the coordinator, shown next to the result because there is no trust model.
 */
@Immutable
class CiRunAttempt(
    val result: CiWorkflowResultEvent?,
    val progress: CiWorkflowProgressEvent?,
    val state: CiState,
    /** The run's conclusion; null while pending, and when the publisher sent a value this client does not know. */
    val conclusion: CiConclusion?,
) {
    val event: Event = result ?: checkNotNull(progress) { "An attempt needs a result or a progress marker" }

    val coordinator: HexKey get() = event.pubKey

    val runId: String? get() = result?.runId() ?: progress?.runId()

    val workflowPath: String? get() = (result?.workflow() ?: progress?.workflow())?.path

    val commit: String? get() = result?.commit() ?: progress?.commit()

    /** The Job Results this attempt quotes. Only quoted jobs are shown: an unquoted 9841 has no attempt to belong to. */
    val jobs: List<CiJobResultQuote> get() = result?.jobResults() ?: progress?.jobResults() ?: emptyList()

    /** Jobs a live marker reports as executing. Empty once the attempt has a result. */
    val inProgressJobs: List<String> get() = if (result == null) progress?.inProgressJobs() ?: emptyList() else emptyList()

    /** The time attempts are ordered by: `started_at`, else `queued_at` (markers are renewed, so not their `created_at`), else `created_at`. */
    val sortTime: Long =
        result?.let { it.startedAt() ?: it.queuedAt() ?: it.createdAt }
            ?: progress?.let { it.startedAt() ?: it.queuedAt() ?: it.createdAt }
            ?: 0L
}

/** Every attempt of one workflow file from one coordinator, newest first; [current] is the one that counts. */
@Immutable
class CiWorkflowRuns(
    val coordinator: HexKey,
    val workflowPath: String?,
    val attempts: List<CiRunAttempt>,
) {
    val current: CiRunAttempt get() = attempts.first()
}

/**
 * The CI picture of one PR or patch at a moment in time. [state] is null when nothing ran.
 * [nextExpiration] is the earliest moment a live marker used here expires, after which the summary
 * must be recomputed so a stale "pending" clears without a new event arriving.
 */
@Immutable
class CiSummary(
    val state: CiState?,
    val workflows: List<CiWorkflowRuns>,
    val nextExpiration: Long?,
) {
    val passed: Int get() = workflows.count { it.current.state == CiState.SUCCESS }

    companion object {
        val EMPTY = CiSummary(null, emptyList(), null)
    }
}

/**
 * Reads the Nostr CI events attached to one PR or patch the way gitworkshop's interpretation rules
 * say to:
 *
 * - each Workflow Result (9842) is its own attempt; attempts are never merged because their context
 *   tags match;
 * - the latest attempt per (workflow, coordinator) is the current one;
 * - an unexpired Workflow Progress (39842) whose status is `queued` / `in_progress` is pending; a
 *   newer marker for the same `d` replaces an older one, and a marker whose run already has a
 *   Workflow Result is superseded by it; expired markers are ignored;
 * - Job Results (9841) are never read here: they only appear inside the attempt that quotes them.
 */
object CiRollup {
    fun stateOf(conclusion: CiConclusion?): CiState =
        when (conclusion) {
            CiConclusion.SUCCESS -> CiState.SUCCESS
            CiConclusion.FAILURE, CiConclusion.TIMED_OUT, CiConclusion.STARTUP_FAILURE -> CiState.FAILURE
            CiConclusion.CANCELLED -> CiState.CANCELLED
            CiConclusion.NEUTRAL, CiConclusion.SKIPPED, null -> CiState.NEUTRAL
        }

    /**
     * The combined state of several workflows, GitHub-style: any failure fails, otherwise anything
     * still running is pending, otherwise a cancellation shows, otherwise success when at least one
     * passed. Null when there is nothing to combine.
     */
    fun combine(states: Collection<CiState>): CiState? =
        when {
            states.isEmpty() -> null
            CiState.FAILURE in states -> CiState.FAILURE
            CiState.PENDING in states -> CiState.PENDING
            CiState.CANCELLED in states -> CiState.CANCELLED
            CiState.SUCCESS in states -> CiState.SUCCESS
            else -> CiState.NEUTRAL
        }

    fun attempts(
        results: Collection<CiWorkflowResultEvent>,
        progress: Collection<CiWorkflowProgressEvent>,
        now: Long,
    ): List<CiRunAttempt> {
        val fromResults = results.distinctBy { it.id }.map { CiRunAttempt(it, null, stateOf(it.conclusion()), it.conclusion()) }

        val concludedRuns = results.mapNotNullTo(HashSet()) { result -> result.runId()?.let { result.pubKey to it } }

        // One marker per address: the newest replaces the earlier state of the same run.
        val latestMarkers =
            progress
                .groupBy { it.pubKey to it.runId() }
                .values
                .map { versions -> versions.maxWith(compareBy<CiWorkflowProgressEvent> { it.createdAt }.thenBy { it.id }) }

        val fromMarkers =
            latestMarkers.mapNotNull { marker ->
                if (!marker.isLive(now)) return@mapNotNull null
                if ((marker.pubKey to marker.runId()) in concludedRuns) return@mapNotNull null
                when (marker.status()) {
                    CiWorkflowStatus.QUEUED, CiWorkflowStatus.IN_PROGRESS -> CiRunAttempt(null, marker, CiState.PENDING, null)
                    CiWorkflowStatus.CONCLUDED -> CiRunAttempt(null, marker, stateOf(marker.conclusion()), marker.conclusion())
                    null -> null
                }
            }

        return fromResults + fromMarkers
    }

    fun summarize(
        results: Collection<CiWorkflowResultEvent>,
        progress: Collection<CiWorkflowProgressEvent>,
        now: Long,
    ): CiSummary {
        val attempts = attempts(results, progress, now)
        if (attempts.isEmpty()) return CiSummary.EMPTY

        val newestFirst = compareByDescending<CiRunAttempt> { it.sortTime }.thenByDescending { it.event.id }

        val workflows =
            attempts
                .groupBy { it.coordinator to it.workflowPath }
                .map { (key, group) -> CiWorkflowRuns(key.first, key.second, group.sortedWith(newestFirst)) }
                .sortedWith(compareBy<CiWorkflowRuns> { it.workflowPath ?: "" }.thenBy { it.coordinator })

        val nextExpiration = attempts.mapNotNull { it.progress?.validExpiration() }.minOrNull()

        return CiSummary(combine(workflows.map { it.current.state }), workflows, nextExpiration)
    }
}
