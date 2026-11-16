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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.rooms.isInRoom
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val RepliesAndReactionsKinds =
    listOf(
        TextNoteEvent.KIND,
        ReactionEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ReportEvent.KIND,
        ZapReceiptEvent.KIND,
        OnchainZapEvent.KIND,
        Bolt12ZapEvent.KIND,
        OtsEvent.KIND,
        TextNoteModificationEvent.KIND,
        CommentEvent.KIND,
        AttestationEvent.KIND,
    )

/**
 * NIP-22 keeps the conversation root in the **uppercase** `E` tag and only the direct
 * parent in lowercase `e`. A comment two or more levels deep therefore never carries
 * `e`=<rootId>, so an `e`-only engagement filter sees only the first level of a
 * 1111 thread — the rest used to appear only once ThreadScreen opened its own `E`
 * subscription. These kinds are the ones that anchor themselves with `E`.
 */
val RootScopedRepliesKinds =
    listOf(
        CommentEvent.KIND,
        // NIP-34 kind 1619 carries the PR it revises in `E` and has no lowercase
        // `e` at all (see the spec's PR Update example), so this filter is the
        // only engagement route to a PR's revision chain.
        GitPullRequestUpdateEvent.KIND,
        // Nostr CI Workflow Results (9842) and Progress markers (39842) name the PR / patch they
        // ran for in `E` (NIP-22 trigger context), so the CI badge rides this fan-out as it does on
        // gitworkshop. Job Results (9841) are left out on purpose: they carry log tails and are
        // fetched by id only when someone opens the runs sheet.
        CiWorkflowResultEvent.KIND,
        CiWorkflowProgressEvent.KIND,
    )

/**
 * What only a NIP-34 issue, patch or PR collects: NIP-32 labels (1985), which rename (`#subject`)
 * and label (`#t`) it after the fact, and the rest of a patch series and its revisions, which name
 * the root patch. Both use `e`. Asked in a filter of their own, for git items only: in the shared
 * [RepliesAndReactionsKinds2] filter, a note that labelling bots tag heavily would fill its limit
 * with labels and push out the statuses, poll responses and bids of every other note in the batch.
 */
val GitItemThreadKinds =
    listOf(
        LabelEvent.KIND,
        GitPatchEvent.KIND,
    )

val RepliesAndReactionsKinds2 =
    listOf(
        DeletionRequestEvent.KIND,
        DvmContentDiscoveryResponseEvent.KIND,
        DvmStatusEvent.KIND,
        TorrentCommentEvent.KIND,
        GitReplyEvent.KIND,
        // NIP-34 status events (1630/1631/1632/1633). Rooted at the target
        // patch/PR/issue via a `root`-marked `e` tag, so an `e=<targetId>`
        // engagement fetch surfaces every open/applied/closed/draft transition —
        // the signal GitStatuses needs to answer isClosedOrResolved() for
        // repo rows. PR revisions (1619) anchor with `E` instead and are pulled
        // by [RootScopedRepliesKinds]; listing them here matched nothing.
        GitStatusOpenEvent.KIND,
        GitStatusAppliedEvent.KIND,
        GitStatusClosedEvent.KIND,
        GitStatusDraftEvent.KIND,
        // NIP-34 cover notes (1624) point at their issue / patch / PR with a lowercase root `e`.
        GitCoverNoteEvent.KIND,
        // Review verdicts (7321) root at the patch / PR with a lowercase `e`.
        GitReviewEvent.KIND,
        PollResponseEvent.KIND,
        ZapPollEvent.KIND,
        // NIP-15 bids and the merchant's confirmations name the auction VERSION by its id (`e`),
        // so an auction card watching that version note fetches them here: its highest bid.
        BidEvent.KIND,
        BidConfirmationEvent.KIND,
    )

fun filterRepliesAndReactionsToNotes(
    events: List<Note>,
    since: SincePerRelayMap?,
    accountPubKey: HexKey? = null,
): List<RelayBasedFilter>? {
    if (events.isEmpty()) return null

    val perRelayGitItemIds =
        mapOfSet {
            events.forEach { note ->
                val event = note.event
                if (event !is GitIssueEvent && event !is GitPatchEvent && event !is GitPullRequestEvent) return@forEach
                note.relayUrlsForReactions().forEach { relay -> add(relay, note.idHex) }
            }
        }

    val perRelayEventIds =
        mapOfSet {
            events.forEach { note ->
                // A private message (a room's, or a NIP-17 rumor) gets its reactions and replies
                // inside its own conversation, never from relays; asking for them would hand the
                // relays the private message's id.
                if (note.isPrivateRumor() || note.isInRoom()) return@forEach

                note.relayUrlsForReactions().forEach { relay ->
                    add(relay, note.idHex)
                }

                // NIP-88 tells respondents to publish their kind-1018 votes to the relays the poll
                // itself declares, and EventBroadcaster obeys that on the way out. Those relays are
                // usually not the author's inbox nor where we saw the poll, so without this the
                // tally silently under-counts — often down to just our own vote.
                (note.event as? PollEvent)?.relays()?.forEach { relay ->
                    add(relay, note.idHex)
                }
            }
        }

    return perRelayEventIds.flatMap {
        val since = since?.get(it.key)?.time
        val sortedList = it.value.sorted()
        val relay = it.key
        if (sortedList.isNotEmpty()) {
            listOf(
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = RepliesAndReactionsKinds,
                            tags = mapOf("e" to sortedList),
                            since = since,
                            // Max amount of "replies" to download on a specific event.
                            limit = 1000,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = RepliesAndReactionsKinds2,
                            tags = mapOf("e" to sortedList),
                            since = since,
                            limit = 100,
                        ),
                ),
                // Its own filter on purpose: tag names inside one filter are ANDed, so folding
                // "E" into the "e" filter above would only match comments carrying both.
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = RootScopedRepliesKinds,
                            tags = mapOf("E" to sortedList),
                            since = since,
                            // Matches the ceiling of the direct-reply filter above: this is the
                            // rest of the same thread, not a second conversation.
                            limit = 1000,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = listOf(TextNoteEvent.KIND, CommentEvent.KIND),
                            tags = mapOf("q" to sortedList),
                            since = since,
                            limit = 1000,
                        ),
                ),
            ) +
                perRelayGitItemIds[relay].orEmpty().sorted().let { gitItemIds ->
                    if (gitItemIds.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(
                            RelayBasedFilter(
                                relay = relay,
                                filter =
                                    ExplainedFilter(
                                        purpose = SubPurpose.ENGAGEMENT,
                                        accountPubKeys = listOfNotNull(accountPubKey),
                                        kinds = GitItemThreadKinds,
                                        tags = mapOf("e" to gitItemIds),
                                        since = since,
                                        limit = 500,
                                    ),
                            ),
                        )
                    }
                }
        } else {
            emptyList()
        }
    }
}
