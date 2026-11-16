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
package com.vitorpamplona.amethyst.commons.nip34Git.status

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.issue.GitClosingReferences
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.earliestUniqueCommit
import com.vitorpamplona.quartz.nip34Git.status.repository

/**
 * The statuses a merge publishes, written the way gitworkshop writes them, for the app and `amy`
 * alike: the applied status of the merged item, and a resolved status for each open issue a
 * merged commit says it closes.
 */
object GitMergeStatuses {
    /** A commit the branch gained: its id and message, which may close issues. */
    class MergedCommit(
        val oid: String,
        val message: String,
    )

    /** [issue] is resolved by [commit], whose message says so on [line]. */
    class Resolution(
        val issue: GitIssueEvent,
        val commit: String,
        val line: String,
    )

    /**
     * The applied status (1631) of [target] merged as [mergeCommit]: the target as `root`, an `a`
     * for each of [coordinates], a `p` for the target's author and each coordinate's owner but
     * [signer], [appliedPatches] as `q`, and the repository's earliest unique commit as `r`.
     */
    fun merged(
        target: EventHintBundle<Event>,
        coordinates: List<Address>,
        earliestUniqueCommit: String?,
        signer: HexKey,
        mergeCommit: String,
        appliedPatches: List<EventHintBundle<GitPatchEvent>> = emptyList(),
        appliedAsCommits: List<String> = emptyList(),
        content: String = "",
        extra: TagArrayBuilder<GitStatusAppliedEvent>.() -> Unit = {},
    ): EventTemplate<GitStatusAppliedEvent> =
        GitStatusAppliedEvent.build(
            content = content,
            target = target,
            appliedPatches = appliedPatches,
            mergeCommit = mergeCommit,
            appliedAsCommits = appliedAsCommits,
            notify =
                coordinates
                    .map { it.pubKeyHex }
                    .filter { it != signer && it != target.event.pubKey }
                    .distinct()
                    .map { PTag(it) },
        ) {
            coordinates.forEach { repository(ATag(it)) }
            earliestUniqueCommit?.let { earliestUniqueCommit(it) }
            extra()
        }

    /**
     * The issues of [issues], filed under one of [coordinates] and still open ([isOpen]), that the
     * messages of [commits] close ([GitClosingReferences]): each once, by the first commit that
     * names it. A `#prefix` resolves only when exactly one issue's id starts with it.
     */
    fun resolutions(
        commits: List<MergedCommit>,
        issues: Collection<GitIssueEvent>,
        coordinates: Set<Address>,
        isOpen: (GitIssueEvent) -> Boolean,
    ): List<Resolution> {
        val filed = issues.filter { issue -> issue.repositoryAddresses().any { it in coordinates } }.distinctBy { it.id }
        val done = HashSet<HexKey>()
        val result = ArrayList<Resolution>()
        commits.forEach { commit ->
            GitClosingReferences.parse(commit.message).forEach { reference ->
                val issue = filed.filter { if (reference.isPrefix) it.id.startsWith(reference.id) else it.id == reference.id }.singleOrNull()
                if (issue != null && issue.id !in done && isOpen(issue)) {
                    done.add(issue.id)
                    result.add(Resolution(issue, commit.oid, reference.line))
                }
            }
        }
        return result
    }

    /** The resolved status of [resolution]'s issue, quoting the line that closed it, as gitworkshop does. */
    fun resolved(
        resolution: Resolution,
        issue: EventHintBundle<Event>,
        mergeCommit: String,
        merged: EventHintBundle<Event>,
        coordinates: List<Address>,
        earliestUniqueCommit: String?,
        signer: HexKey,
    ): EventTemplate<GitStatusAppliedEvent> {
        val content =
            buildString {
                append(resolution.line).append("\n\nresolved by commit ").append(resolution.commit)
                if (mergeCommit != resolution.commit) append(", when merged in commit ").append(mergeCommit)
            }
        return merged(issue, coordinates, earliestUniqueCommit, signer, mergeCommit, content = content) {
            alt("issue resolved from commit message")
            add(arrayOf("c", resolution.commit))
            add(QEventTag.assemble(merged.event.id, merged.relay, merged.event.pubKey))
        }
    }
}
