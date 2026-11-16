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
package com.vitorpamplona.amethyst.commons.nip34Git.merge

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitPrivateRepositoryAuth
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitMergeStatuses
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.amethyst.commons.nip34Git.status.publishGitApplied
import com.vitorpamplona.amethyst.commons.nip34Git.status.publishGitIssueResolved
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip34Git.issue.GitClosingReferences
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.utils.Log
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/** Where a merge is, for a progress line. */
enum class GitMergeStep { PUBLISHING_STATE_AND_PUSHING, PUBLISHING_STATUS }

/** How a merge ended. */
sealed interface GitMergeOutcome {
    /** [branch] moved to [newTip] on [push]'s servers; [resolvedIssues] were marked resolved by its commits. */
    class Merged(
        val push: GitMergePush,
        val branch: String,
        val newTip: String,
        val resolvedIssues: List<GitMergeStatuses.Resolution>,
        /** False when the push landed but the "merged" status could not be signed or sent. */
        val markedMerged: Boolean = true,
    ) : GitMergeOutcome

    /** No server took the push: nothing but the signed state went out, and it named a tip no server has. */
    class PushFailed(
        val reason: String,
    ) : GitMergeOutcome
}

/**
 * The app's side of a merge ([GitMergeEngine]): reads the item, its series or PR tip, the
 * repository's announcements and signed state from the cache, and after the push publishes what
 * gitworkshop publishes: the new state to the repository's relays and the user's outbox, the
 * applied status (1631) naming the merge commit, and a resolved status on every open issue a
 * merged commit says it closes.
 */
class GitMerger(
    private val account: Account,
    okHttpClient: (String) -> OkHttpClient,
    private val cache: LocalCache = account.cache,
    private val authority: GitAuthority = LocalCache.gitAuthority,
) {
    private val engine = GitMergeEngine(okHttpClient, GitPrivateRepositoryAuth(account.signer, cache))

    /** Null when [item] is not a PR or a series' root patch, or its repository hasn't loaded. */
    fun inputsFor(item: Note): GitMergeInputs? {
        val event = item.event ?: return null
        if (event !is GitPullRequestEvent && event !is GitPatchEvent) return null
        val repository = GitAuthority.repositoriesOf(event).firstOrNull() ?: return null
        val membership = authority.membership(repository) ?: return null

        val states =
            membership.confirmedMaintainers.mapNotNull { maintainer ->
                cache.getAddressableNoteIfExists(Address(GitRepositoryStateEvent.KIND, maintainer, repository.dTag))?.event as? GitRepositoryStateEvent
            }
        // A revision (a revised series, or the PR a series became) threads onto the original.
        val threaded = item.replies.mapNotNull { it.event }
        return GitMergeInputs.build(
            item = event,
            threaded = threaded,
            seriesOf = { root -> cache.getNoteIfExists(root.id)?.let { GitPatchSeries.of(it) } ?: listOf(root) },
            updatesOf = { pr -> cache.getNoteIfExists(pr.id)?.let { GitPullRequestRevisions.updates(it, authority) } ?: emptyList() },
            repository = repository,
            announcements = membership.confirmedAnnouncements,
            states = states,
            authority = authority,
            authorName =
                cache
                    .getUserIfExists(event.pubKey)
                    ?.metadataOrNull()
                    ?.flow
                    ?.value
                    ?.info
                    ?.bestName(),
            relayHint = item.relayHintUrl(),
        )
    }

    /** Plans the merge of [item], fetching what it needs from the repository's servers. */
    suspend fun check(
        item: Note,
        onStep: (GitMergeCheckStep) -> Unit = {},
    ): GitMergeCheck {
        onStep(GitMergeCheckStep.STATE)
        refreshSignedStates(item)
        val inputs = inputsFor(item) ?: return GitMergeCheck.Blocked(GitMergeBlocker.REPOSITORY_NOT_LOADED)
        val me = account.userProfile()
        val info =
            me
                .metadataOrNull()
                ?.flow
                ?.value
                ?.info
        val name = info?.bestName()?.takeIf { it.isNotBlank() } ?: "Anonymous"
        val email = info?.nip05?.takeIf { it.isNotBlank() } ?: "${me.pubkeyNpub()}@nostr"
        val check = engine.check(inputs, GitMergeEngine.committer(name, email), onStep)
        if (check !is GitMergeCheck.Ready || !check.merge.isDefaultBranch) return check
        // Which issues it will resolve, so the confirmation can say so.
        if (check.merge.newCommits.none { GitClosingReferences.parse(it.message).isNotEmpty() }) return check
        onStep(GitMergeCheckStep.ISSUES)
        return GitMergeCheck.Ready(check.merge, resolutionsFor(check.merge))
    }

    /**
     * Fetches the maintainers' signed states (kind 30618) from the repository's relays. A thread
     * never loads them, and a merge must start from the newest one: a GRASP server refuses a push
     * whose state names a tip it doesn't hold.
     */
    private suspend fun refreshSignedStates(item: Note) {
        val event = item.event ?: return
        val repository = GitAuthority.repositoriesOf(event).firstOrNull() ?: return
        val membership = authority.membership(repository) ?: return
        val relays = membership.confirmedAnnouncements.flatMapTo(HashSet()) { it.relaysNorm() }
        if (relays.isEmpty()) return
        val filter = Filter(kinds = listOf(GitRepositoryStateEvent.KIND), authors = membership.confirmedMaintainers, tags = mapOf("d" to listOf(repository.dTag)))
        consume(relays, listOf(filter))
    }

    /**
     * Signs, pushes and publishes [merge]. [resolutions] are the issues the check found its commits
     * close; each is resolved unless it closed meanwhile.
     */
    suspend fun merge(
        item: Note,
        merge: GitPreparedMerge,
        resolutions: List<GitMergeStatuses.Resolution> = emptyList(),
        onStep: (GitMergeStep) -> Unit = {},
    ): GitMergeOutcome {
        onStep(GitMergeStep.PUBLISHING_STATE_AND_PUSHING)
        val push = engine.push(merge, account.signer, account.client)
        if (!push.result.isSuccess) return GitMergeOutcome.PushFailed(push.result.describeFailures())

        // The state the servers took, now everywhere the repository is read.
        cache.justConsumeMyOwnEvent(push.state)
        // A private repository's state stays on its own relays (GRASP-08).
        val outbox = if (merge.inputs.announcements.any { it.isPrivate() }) emptySet() else account.outboxRelays.flow.value
        val relays = merge.inputs.announcements.flatMapTo(HashSet()) { it.relaysNorm() } + merge.targets.map { it.relay } + outbox
        account.client.publish(push.state, relays)

        // The branch has moved: from here on a failure (a signer that times out, say) must not
        // read as "nothing happened", or the person merges again.
        onStep(GitMergeStep.PUBLISHING_STATUS)
        return try {
            // The series as planned: a patch that arrived since was not merged.
            val patches = merge.inputs.patches.map { cache.getOrCreateNote(it.id) }
            val status =
                account.publishGitApplied(
                    item = item,
                    mergeCommit = merge.plan.mergeCommit,
                    appliedPatches = patches,
                    appliedAsCommits = merge.plan.appliedCommits,
                    authority = authority,
                )
            val resolved = if (merge.isDefaultBranch && status != null) resolveIssues(item, merge, resolutions) else emptyList()
            GitMergeOutcome.Merged(push, merge.branch, merge.plan.newTip, resolved, markedMerged = status != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "merged ${merge.plan.newTip}, but could not publish its status", e)
            GitMergeOutcome.Merged(push, merge.branch, merge.plan.newTip, emptyList(), markedMerged = false)
        }
    }

    /**
     * The open issues of this repository that [merge]'s commits close ([GitMergeStatuses.resolutions]),
     * fetched from its relays (a thread opened from a notification has loaded none): the issues a
     * full id names by id, every issue only when a `#prefix` must be matched, and the statuses of
     * just the issues named, so an issue known closed is not resolved again.
     */
    private suspend fun resolutionsFor(merge: GitPreparedMerge): List<GitMergeStatuses.Resolution> {
        val commits = merge.newCommits.map { GitMergeStatuses.MergedCommit(it.oid, it.message) }
        val references = commits.flatMap { GitClosingReferences.parse(it.message) }
        if (references.isEmpty()) return emptyList()
        val coordinates = authority.coordinatesToTag(merge.inputs.repository).toSet()
        val relays = merge.inputs.announcements.flatMapTo(HashSet()) { it.relaysNorm() }

        if (relays.isNotEmpty()) {
            val fullIds = references.filter { !it.isPrefix }.map { it.id }
            val filters =
                listOfNotNull(
                    fullIds.takeIf { it.isNotEmpty() }?.let { Filter(ids = it, kinds = listOf(GitIssueEvent.KIND)) },
                    if (references.any { it.isPrefix }) Filter(kinds = listOf(GitIssueEvent.KIND), tags = mapOf("a" to coordinates.map { it.toValue() })) else null,
                )
            consume(relays, filters)
        }
        val issues = repositoryIssues(merge)
        val named = GitMergeStatuses.resolutions(commits, issues.values.mapNotNull { it.event as? GitIssueEvent }, coordinates) { true }
        if (named.isEmpty()) return emptyList()
        if (relays.isNotEmpty()) {
            val statusKinds = listOf(GitStatusEvent.KIND_OPEN, GitStatusEvent.KIND_APPLIED, GitStatusEvent.KIND_CLOSED, GitStatusEvent.KIND_DRAFT)
            consume(relays, listOf(Filter(kinds = statusKinds, tags = mapOf("e" to named.map { it.issue.id }))))
        }
        return GitMergeStatuses.resolutions(commits, named.map { it.issue }, coordinates) { issue ->
            issues[issue.id]?.let { !GitStatuses.isClosedOrResolved(it, authority) } ?: false
        }
    }

    private fun repositoryIssues(merge: GitPreparedMerge): Map<String, Note> {
        val issues = HashMap<String, Note>()
        cache.getGitRepositoryIfExists(merge.inputs.repository.dTag)?.items?.forEach { id, note -> if (note.event is GitIssueEvent) issues[id] = note }
        return issues
    }

    /** Fetches [filters] from [relays] into the cache, waiting at most [RELAY_IDLE_MS] for a quiet relay. */
    private suspend fun consume(
        relays: Set<NormalizedRelayUrl>,
        filters: List<Filter>,
    ) {
        if (filters.isEmpty()) return
        account.client
            .fetchAll(filters = relays.associateWith { filters }, idleTimeoutMs = RELAY_IDLE_MS)
            .forEach { cache.justConsume(it, null, wasVerified = false) }
    }

    /** Marks resolved each of [planned] still open, quoting the line that closed it, as gitworkshop does. */
    private suspend fun resolveIssues(
        item: Note,
        merge: GitPreparedMerge,
        planned: List<GitMergeStatuses.Resolution>,
    ): List<GitMergeStatuses.Resolution> =
        planned.filter { resolution ->
            val issue = cache.getNoteIfExists(resolution.issue.id) ?: return@filter false
            if (GitStatuses.isClosedOrResolved(issue, authority)) return@filter false
            account.publishGitIssueResolved(issue, resolution, merge.plan.mergeCommit, item, authority) != null
        }

    companion object {
        private const val TAG = "GitMerger"

        /** These are small queries: a relay quiet this long has nothing more to send. */
        private const val RELAY_IDLE_MS = 3_000L
    }
}
