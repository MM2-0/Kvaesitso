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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip34Git.status.earliestUniqueCommit
import com.vitorpamplona.quartz.nip34Git.status.repository

/**
 * Signs and broadcasts a NIP-34 status of [kind] (`GitStatusEvent.KIND_*`) on [item], an issue,
 * patch or PR. Tagged the way gitworkshop writes it: the item as the `root` `e`, an `a` for every
 * confirmed member's coordinate, a `p` for the item's author and each coordinate's owner, and
 * the repository's earliest unique commit as `r`. The broadcast reaches the repository's relays
 * through those links.
 */
suspend fun Account.publishGitStatus(
    item: Note,
    kind: Int,
    authority: GitAuthority = LocalCache.gitAuthority,
): GitStatusEvent? {
    val hint = item.toEventHint<Event>() ?: return null
    val coordinates = authority.coordinatesToTag(hint.event)
    val euc = coordinates.firstOrNull()?.let(authority::earliestUniqueCommit)
    val notify =
        coordinates
            .map { it.pubKeyHex }
            .filter { it != signer.pubKey && it != hint.event.pubKey }
            .distinct()
            .map { PTag(it) }

    val template =
        when (kind) {
            GitStatusEvent.KIND_OPEN -> GitStatusOpenEvent.build("", hint, notify = notify) { fileUnder(coordinates, euc) }
            GitStatusEvent.KIND_APPLIED -> GitStatusAppliedEvent.build("", hint, notify = notify) { fileUnder(coordinates, euc) }
            GitStatusEvent.KIND_CLOSED -> GitStatusClosedEvent.build("", hint, notify = notify) { fileUnder(coordinates, euc) }
            GitStatusEvent.KIND_DRAFT -> GitStatusDraftEvent.build("", hint, notify = notify) { fileUnder(coordinates, euc) }
            else -> return null
        }
    return signAndComputeBroadcast(template)
}

/**
 * Signs and broadcasts the applied status (1631) of a merge made here: [item] merged as
 * [mergeCommit], quoting the [appliedPatches] of a series (`q`) and the commits it became, tagged
 * as [GitMergeStatuses.merged] writes it.
 */
suspend fun Account.publishGitApplied(
    item: Note,
    mergeCommit: String,
    appliedPatches: List<Note> = emptyList(),
    appliedAsCommits: List<String> = emptyList(),
    authority: GitAuthority = LocalCache.gitAuthority,
): GitStatusAppliedEvent? {
    val hint = item.toEventHint<Event>() ?: return null
    val coordinates = authority.coordinatesToTag(hint.event)
    val template =
        GitMergeStatuses.merged(
            target = hint,
            coordinates = coordinates,
            earliestUniqueCommit = coordinates.firstOrNull()?.let(authority::earliestUniqueCommit),
            signer = signer.pubKey,
            mergeCommit = mergeCommit,
            appliedPatches = appliedPatches.mapNotNull { it.toEventHint<GitPatchEvent>() },
            appliedAsCommits = appliedAsCommits,
        )
    return signAndComputeBroadcast(template)
}

/** Signs and broadcasts the resolved status of an issue a merged commit closed ([GitMergeStatuses.resolved]). */
suspend fun Account.publishGitIssueResolved(
    issue: Note,
    resolution: GitMergeStatuses.Resolution,
    mergeCommit: String,
    merged: Note,
    authority: GitAuthority = LocalCache.gitAuthority,
): GitStatusAppliedEvent? {
    val issueHint = issue.toEventHint<Event>() ?: return null
    val mergedHint = merged.toEventHint<Event>() ?: return null
    val coordinates = authority.coordinatesToTag(issueHint.event)
    val template =
        GitMergeStatuses.resolved(
            resolution = resolution,
            issue = issueHint,
            mergeCommit = mergeCommit,
            merged = mergedHint,
            coordinates = coordinates,
            earliestUniqueCommit = coordinates.firstOrNull()?.let(authority::earliestUniqueCommit),
            signer = signer.pubKey,
        )
    return signAndComputeBroadcast(template)
}

private fun <T : GitStatusEvent> TagArrayBuilder<T>.fileUnder(
    coordinates: List<Address>,
    earliestUniqueCommit: String?,
) {
    coordinates.forEach { repository(ATag(it)) }
    earliestUniqueCommit?.let { earliestUniqueCommit(it) }
}
