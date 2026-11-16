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

import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitMergeStatuses
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth
import com.vitorpamplona.quartz.nip34Git.git.GitPerson
import com.vitorpamplona.quartz.nip34Git.git.GitPushClient
import com.vitorpamplona.quartz.nip34Git.git.GitSmartHttpTransport
import com.vitorpamplona.quartz.nip34Git.merge.GitMergeMessage
import com.vitorpamplona.quartz.nip34Git.merge.GitMergePlan
import com.vitorpamplona.quartz.nip34Git.merge.GitMergePlanner
import com.vitorpamplona.quartz.nip34Git.merge.GitMergeStage
import com.vitorpamplona.quartz.nip34Git.merge.GitObjectSource
import com.vitorpamplona.quartz.nip34Git.merge.GraspPushResult
import com.vitorpamplona.quartz.nip34Git.merge.GraspPusher
import com.vitorpamplona.quartz.nip34Git.merge.GraspTarget
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import okhttp3.OkHttpClient
import java.util.TimeZone

/**
 * Everything a merge reads about an item and its repository, gathered by the caller (the app from
 * its cache, `amy` from relays) and assembled by [build], so the merge is the same for both.
 *
 * @param item the pull request (1618) or the root patch (1617) the thread, its title and its
 *   status hang on.
 * @param source what is merged: [item], or its latest revision (a revised series, or the pull
 *   request a series became).
 * @param patches for a patch series, [source]'s patches in order.
 * @param pullRequestTip for a pull request, [source]'s current tip: its latest authorised update's, else its own.
 * @param pullRequestClones where that tip is served: the update's and the PR's clone URLs.
 * @param announcements the repository's confirmed announcements: their clone URLs are pushed to.
 * @param signedState the maintainers' latest signed state (kind 30618), the branch tips a merge starts from.
 * @param description the current cover note ([descriptionIsCoverNote]), else the item's own text.
 */
class GitMergeInputs(
    val item: Event,
    val source: Event,
    val patches: List<GitPatchEvent>,
    val pullRequestTip: String?,
    val pullRequestClones: List<String>,
    val repository: Address,
    val announcements: List<GitRepositoryEvent>,
    val signedState: GitSignedState?,
    val title: String,
    val nostrUri: String,
    val authorName: String?,
    val authorNpub: String,
    val description: String?,
    val descriptionIsCoverNote: Boolean,
) {
    companion object {
        /**
         * The inputs of merging [item] from the events about it: [threaded] holds what replies to it
         * (revisions, PR updates, cover notes, renames), [seriesOf] a series root's own patches.
         * Only what [authority] counts is read: a stranger's revision, update, cover note or
         * rename never reaches a merge.
         */
        fun build(
            item: Event,
            threaded: Collection<Event>,
            seriesOf: (GitPatchEvent) -> List<GitPatchEvent>,
            updatesOf: (GitPullRequestEvent) -> Collection<GitPullRequestUpdateEvent>,
            repository: Address,
            announcements: List<GitRepositoryEvent>,
            states: List<GitRepositoryStateEvent>,
            authority: GitAuthority,
            authorName: String?,
            relayHint: NormalizedRelayUrl?,
        ): GitMergeInputs {
            val source = (if (item is GitPatchEvent) GitPatchSeries.latestRevision(item, threaded, authority) else null) ?: item
            val pr = source as? GitPullRequestEvent
            val update = pr?.let { GitPullRequestRevisions.authorised(updatesOf(it), it, authority).lastOrNull() }
            val coverNote =
                GitCoverNotes
                    .authorised(threaded.filterIsInstance<GitCoverNoteEvent>(), item, authority)
                    .lastOrNull()
                    ?.content
                    ?.takeIf { it.isNotBlank() }
            val ownText =
                when (item) {
                    is GitPullRequestEvent -> item.content
                    is GitPatchEvent -> item.description()
                    else -> null
                }?.takeIf { it.isNotBlank() }
            return GitMergeInputs(
                item = item,
                source = source,
                patches = (source as? GitPatchEvent)?.let(seriesOf) ?: emptyList(),
                pullRequestTip = update?.currentCommit() ?: pr?.currentCommit(),
                pullRequestClones = ((update?.cloneUrls() ?: emptyList()) + (pr?.cloneUrls() ?: emptyList())).distinct(),
                repository = repository,
                announcements = announcements,
                signedState = GitSignedState.latest(states),
                title = GitItemTitles.titleOf(item, threaded.filterIsInstance<LabelEvent>(), authority) ?: "",
                nostrUri = NEvent.createNostrUri(item.id, item.pubKey, item.kind, relayHint),
                authorName = authorName,
                authorNpub = NPub.create(item.pubKey),
                description = coverNote ?: ownText,
                descriptionIsCoverNote = coverNote != null,
            )
        }
    }
}

/**
 * Where a merge goes: the GRASP servers it pushes to, and the [others] (GitHub, a self-hosted
 * forge) it cannot, which keep the old tip until a maintainer pushes there with git.
 */
class GitMergeTargets(
    val grasp: List<GraspTarget>,
    val others: List<String>,
) {
    companion object {
        fun of(announcements: List<GitRepositoryEvent>): GitMergeTargets {
            val grasp = announcements.flatMap(GraspTarget::of).distinctBy { it.cloneUrl }
            val pushed = grasp.mapTo(HashSet()) { it.cloneUrl }
            val others = announcements.flatMap { it.clones() }.distinct().filter { it !in pushed }
            return GitMergeTargets(grasp, others)
        }
    }
}

/** Why a merge can't be made in the app; the maintainer merges with git instead. */
enum class GitMergeBlocker {
    /** The item's repository announcement has not loaded yet. */
    REPOSITORY_NOT_LOADED,

    /** No clone URL is a GRASP server: only a local `git push` can reach them. */
    NOT_ON_GRASP,

    /** The maintainers never signed a state (kind 30618), so there is no branch tip to start from. */
    NO_SIGNED_STATE,

    /** The signed state has no tip for the branch the item targets. */
    NO_BRANCH_TIP,

    /** The pull request names no tip commit. */
    NO_PULL_REQUEST_TIP,
}

/** Where a check is, for its progress line: each is a round trip to the relays or servers. */
enum class GitMergeCheckStep {
    STATE,
    HISTORY,
    CHANGES,
    MERGING,
    ISSUES,
    ;

    companion object {
        fun of(stage: GitMergeStage) =
            when (stage) {
                GitMergeStage.HISTORY -> HISTORY
                GitMergeStage.CHANGES -> CHANGES
                GitMergeStage.MERGING -> MERGING
            }
    }
}

/** What merging an item here would do, before anything is signed. */
sealed interface GitMergeCheck {
    /** What merging by hand needs: where the pull request's [tip] is served ([clone]), or that it is a patch series. */
    class Hint(
        val clone: String?,
        val tip: String?,
        val isPatchSeries: Boolean,
    )

    class Blocked(
        val blocker: GitMergeBlocker,
        val merge: Hint? = null,
    ) : GitMergeCheck

    class Conflicts(
        val paths: List<String>,
        val merge: Hint? = null,
    ) : GitMergeCheck

    object AlreadyMerged : GitMergeCheck

    class Failed(
        val message: String,
    ) : GitMergeCheck

    /** [resolutions]: the open issues its commits close, which the merge will mark resolved. */
    class Ready(
        val merge: GitPreparedMerge,
        val resolutions: List<GitMergeStatuses.Resolution> = emptyList(),
    ) : GitMergeCheck
}

/**
 * A merge ready to publish: [branch] moves from [oldTip] to [plan]'s new tip on [targets]; the
 * [otherClones] are git hosts the push cannot reach.
 * [newCommits] are the commits the branch gains (the merge commit aside), whose messages may
 * resolve issues; [isDefaultBranch] says whether they count for that (only the default branch's do).
 */
class GitPreparedMerge(
    val inputs: GitMergeInputs,
    val branch: String,
    val isDefaultBranch: Boolean,
    val oldTip: String,
    val plan: GitMergePlan.Ready,
    val targets: List<GraspTarget>,
    val newCommits: List<GitCommit>,
    val otherClones: List<String> = emptyList(),
)

/** The signed state a merge published, and how each GRASP server took the push. */
class GitMergePush(
    val state: GitRepositoryStateEvent,
    val result: GraspPushResult,
)

/**
 * Merges a NIP-34 pull request or patch series on the repository's GRASP servers, as gitworkshop
 * does in the browser: plan the merge from the servers' objects ([check]), then sign the new
 * state, publish it to each server's relay and push to the servers whose relay took it ([push]).
 * The applied status and the state's wider broadcast follow a successful push, by the caller.
 */
class GitMergeEngine(
    okHttpClient: (String) -> OkHttpClient,
    /** Signs the requests of a private repository (GRASP-08); null when none needs it. */
    auth: GitHttpAuth?,
) {
    /** For public repositories only. Its own constructor, so a trailing lambda (`GitMergeEngine { client }`) is still the client. */
    constructor(okHttpClient: (String) -> OkHttpClient) : this(okHttpClient, null)

    private val transport = GitSmartHttpTransport(okHttpClient, auth)
    private val pushClient = GitPushClient(okHttpClient, "amethyst", auth)

    suspend fun check(
        inputs: GitMergeInputs,
        committer: GitPerson,
        onStep: (GitMergeCheckStep) -> Unit = {},
    ): GitMergeCheck {
        // Gitworkshop merges only when every clone URL is a GRASP server; a repository mirrored to
        // GitHub as well merges here too, and the dialog says the mirror needs a push with git.
        val split = GitMergeTargets.of(inputs.announcements)
        val targets = split.grasp
        val hint =
            GitMergeCheck.Hint(
                clone = (inputs.pullRequestClones + targets.map { it.cloneUrl } + split.others).firstOrNull(),
                tip = inputs.pullRequestTip,
                isPatchSeries = inputs.source is GitPatchEvent,
            )
        if (targets.isEmpty()) return GitMergeCheck.Blocked(GitMergeBlocker.NOT_ON_GRASP, hint)

        val state = inputs.signedState ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_SIGNED_STATE, hint)
        val pr = inputs.source as? GitPullRequestEvent
        val branch = pr?.targetBranch() ?: state.headBranch ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_BRANCH_TIP, hint)
        val oldTip = state.commitFor(branch) ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_BRANCH_TIP, hint)

        val clones = targets.map { it.cloneUrl }
        // The repository's servers first; a PR's blob may be only on the PR's own servers.
        val source = GitObjectSource(transport, (clones + inputs.pullRequestClones + split.others).distinct())
        val message =
            GitMergeMessage.build(
                rootId = inputs.item.id,
                title = inputs.title,
                nostrUri = inputs.nostrUri,
                authorName = inputs.authorName,
                authorNpub = inputs.authorNpub,
                description = inputs.description,
                descriptionIsCoverNote = inputs.descriptionIsCoverNote,
            )

        val plan =
            if (pr != null) {
                val tip = inputs.pullRequestTip ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_PULL_REQUEST_TIP)
                // A GRASP server keeps each PR's commits under refs/nostr/<id>, so the repository's
                // own servers come after the PR's.
                val prSource = GitObjectSource(transport, (inputs.pullRequestClones + clones + split.others).distinct())
                GitMergePlanner(source, progress = { onStep(GitMergeCheckStep.of(it)) }).planPullRequest(oldTip, tip, prSource, committer, message)
            } else {
                GitMergePlanner(source, progress = { onStep(GitMergeCheckStep.of(it)) }).planPatches(oldTip, inputs.patches, committer, message)
            }

        return when (plan) {
            is GitMergePlan.AlreadyMerged -> GitMergeCheck.AlreadyMerged
            is GitMergePlan.Conflicts -> GitMergeCheck.Conflicts(plan.paths, hint)
            is GitMergePlan.Failed -> GitMergeCheck.Failed(plan.reason)
            is GitMergePlan.Ready -> GitMergeCheck.Ready(GitPreparedMerge(inputs, branch, branch == state.headBranch, oldTip, plan, targets, plan.newCommits, split.others))
        }
    }

    /** Signs the state that moves the branch and pushes it; nothing else is published. */
    suspend fun push(
        merge: GitPreparedMerge,
        signer: NostrSigner,
        client: INostrClient,
    ): GitMergePush {
        val previous = merge.inputs.signedState?.event
        val template =
            GitRepositoryStateEvent.advance(
                previous = previous,
                dTag = merge.inputs.repository.dTag,
                branch = merge.branch,
                commit = merge.plan.newTip,
                makeHead = merge.isDefaultBranch,
            )
        val state = signer.sign(template)
        val result = GraspPusher(client, pushClient).push(state, merge.targets, merge.branch, merge.plan.newTip, merge.plan.objects, merge.plan.branch)
        return GitMergePush(state, result)
    }

    companion object {
        /** The committer of a merge made now by [name] (`<email>`), in this device's time zone, as gitworkshop writes it. */
        fun committer(
            name: String,
            email: String,
            nowSec: Long = System.currentTimeMillis() / 1000,
        ): GitPerson = GitPerson(name, email, nowSec, TimeZone.getDefault().getOffset(nowSec * 1000) / 60_000)
    }
}
