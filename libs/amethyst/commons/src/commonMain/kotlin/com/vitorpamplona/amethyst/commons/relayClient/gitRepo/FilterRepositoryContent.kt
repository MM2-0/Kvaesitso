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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

/** A repository's items: its issues, patches and pull requests. */
val RepositoryItemKinds =
    listOf(
        GitIssueEvent.KIND,
        GitPatchEvent.KIND,
        GitPullRequestEvent.KIND,
    )

/**
 * What happens to those items that names the repository too: PR updates and statuses carrying an
 * `a`. A filter of its own, so a busy repository's statuses cannot use up the items' limit: on ngit
 * one filter for both, at 500, left a third of the issues unloaded. Statuses without an `a`, and
 * labels and cover notes (which never carry one), come by `#e` ([filterRepositoryItemEssentials]).
 */
val RepositoryActivityKinds =
    listOf(
        GitPullRequestUpdateEvent.KIND,
        GitStatusOpenEvent.KIND,
        GitStatusAppliedEvent.KIND,
        GitStatusClosedEvent.KIND,
        GitStatusDraftEvent.KIND,
    )

fun filterRepositoryContent(
    relay: NormalizedRelayUrl,
    repository: GitRepositoryEvent,
    since: Long?,
): List<RelayBasedFilter> =
    listOf(RepositoryItemKinds, RepositoryActivityKinds).map { kinds ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.TOPIC_FEED,
                    tags = mapOf("a" to repositoryCoordinates(repository)),
                    kinds = kinds,
                    limit = 500,
                    since = since,
                ),
        )
    }

/**
 * What decides an item's state but may not name the repository: its statuses (older ngit wrote
 * them with only an `e`), its labels and renames (1985), its cover notes (1624) and their
 * withdrawals (5), all by `#e`, and its PR updates by `#E`. gitworkshop loads the same set for every
 * listed item (`ItemEssentialEventsModel`), so both clients count states over the same events.
 */
val RepositoryItemEssentialKinds =
    listOf(
        GitStatusOpenEvent.KIND,
        GitStatusAppliedEvent.KIND,
        GitStatusClosedEvent.KIND,
        GitStatusDraftEvent.KIND,
        LabelEvent.KIND,
        GitCoverNoteEvent.KIND,
        GitReviewEvent.KIND,
        DeletionRequestEvent.KIND,
    )

/** Item ids per filter: a relay clamps a filter's results, and each item has a few essentials. */
const val ITEM_ESSENTIALS_IDS_PER_FILTER = 100

fun filterRepositoryItemEssentials(
    relay: NormalizedRelayUrl,
    itemIds: Collection<HexKey>,
    since: Long?,
): List<RelayBasedFilter> =
    itemIds.sorted().chunked(ITEM_ESSENTIALS_IDS_PER_FILTER).flatMap { ids ->
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter = ExplainedFilter(purpose = SubPurpose.TOPIC_FEED, tags = mapOf("e" to ids), kinds = RepositoryItemEssentialKinds + GitReplyEvent.KIND, since = since),
            ),
            RelayBasedFilter(
                relay = relay,
                // The items' comments too: the lists show how many each has.
                filter = ExplainedFilter(purpose = SubPurpose.TOPIC_FEED, tags = mapOf("E" to ids), kinds = listOf(GitPullRequestUpdateEvent.KIND, CommentEvent.KIND), since = since),
            ),
        )
    }

/**
 * The CI kinds a repository's PR / patch list needs for its status badges: Workflow Results (9842,
 * no content, small) and live Workflow Progress markers (39842, NIP-40-expiring, so the live set
 * stays small). Kept out of [RepositoryContentKinds] so a busy CI cannot use up that filter's limit
 * and push issues and PRs out of the list. Job Results (9841) carry log tails and are fetched by id
 * only when someone opens the runs sheet.
 */
val RepositoryCiKinds =
    listOf(
        CiWorkflowResultEvent.KIND,
        CiWorkflowProgressEvent.KIND,
    )

/**
 * Every coordinate an issue, PR, patch or CI event may tag the repository with: they carry one `a`
 * per maintainer announcement, so the owner's coordinate alone would miss items filed under a
 * co-maintainer's. Display then keeps only confirmed members' coordinates.
 */
fun repositoryCoordinates(repository: GitRepositoryEvent): List<String> = repository.namedPubKeys().map { Address.assemble(GitRepositoryEvent.KIND, it, repository.dTag()) }

fun filterRepositoryCi(
    relay: NormalizedRelayUrl,
    repository: GitRepositoryEvent,
    since: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.TOPIC_FEED,
                tags = mapOf("a" to repositoryCoordinates(repository)),
                kinds = RepositoryCiKinds,
                limit = 300,
                since = since,
            ),
    )
