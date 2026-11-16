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
package com.vitorpamplona.amethyst.commons.nip34Git.pr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent

/** One revision of a NIP-34 pull request: its tip commit and the merge base it sits on. */
@Immutable
data class GitPullRequestRevision(
    val commit: String?,
    val mergeBase: String?,
) {
    /** True when [other] moved the PR onto a different merge base, i.e. it was rebased. */
    fun rebasedFrom(other: GitPullRequestRevision): Boolean = mergeBase != null && other.mergeBase != null && mergeBase != other.mergeBase

    companion object {
        fun of(pullRequest: GitPullRequestEvent) = GitPullRequestRevision(pullRequest.currentCommit().nonBlank(), pullRequest.mergeBase().nonBlank())

        fun of(update: GitPullRequestUpdateEvent) = GitPullRequestRevision(update.currentCommit().nonBlank(), update.mergeBase().nonBlank())

        private fun String?.nonBlank() = this?.takeIf { it.isNotBlank() }
    }
}

/**
 * How a pull request's tip moved over time. A PR (kind 1618) names its first tip; each PR update
 * (kind 1619) names a newer one. Updates are ordered by `created_at`; at equal timestamps the lower
 * event id counts as the later one, as gitworkshop picks it. The last in [order] is the current tip.
 */
object GitPullRequestRevisions {
    val order: Comparator<GitPullRequestUpdateEvent> = compareBy<GitPullRequestUpdateEvent> { it.createdAt }.thenByDescending { it.id }

    /**
     * [pullRequest]'s updates, oldest first, from the ones the cache threads onto it: only those its
     * author or a confirmed maintainer signed, as of the update ([GitAuthority.isMaintainerAt]).
     * Anyone else's would let a stranger move the tip and the clone URL "View changes" fetches from.
     */
    fun updates(
        pullRequest: Note,
        authority: GitAuthority,
    ): List<GitPullRequestUpdateEvent> {
        val pr = pullRequest.event as? GitPullRequestEvent ?: return emptyList()
        return authorised(pullRequest.replies.mapNotNull { it.event as? GitPullRequestUpdateEvent }, pr, authority)
    }

    /** The update that set [pullRequest]'s current tip, or null when it has none that counts. */
    fun latest(
        pullRequest: Note,
        authority: GitAuthority,
    ): GitPullRequestUpdateEvent? = updates(pullRequest, authority).lastOrNull()

    /** [updates] for updates held outside the cache. */
    fun authorised(
        updates: Collection<GitPullRequestUpdateEvent>,
        pullRequest: GitPullRequestEvent,
        authority: GitAuthority,
    ): List<GitPullRequestUpdateEvent> =
        updates
            .filter { it.parentPullRequestId() == pullRequest.id && authority.isMaintainerAt(it.pubKey, pullRequest, it.createdAt) }
            .sortedWith(order)

    /**
     * The revision [update] replaced: the update of the same PR just before it, or the PR's own tip
     * when [update] is the first one. Null when that earlier event isn't known (the PR hasn't loaded).
     */
    fun previous(
        update: GitPullRequestUpdateEvent,
        updatesOfThePullRequest: Collection<GitPullRequestUpdateEvent>,
        pullRequest: GitPullRequestEvent?,
    ): GitPullRequestRevision? {
        val earlier =
            updatesOfThePullRequest
                .filter { it.id != update.id && order.compare(it, update) < 0 }
                .maxWithOrNull(order)

        return when {
            earlier != null -> GitPullRequestRevision.of(earlier)
            pullRequest != null -> GitPullRequestRevision.of(pullRequest)
            else -> null
        }
    }
}
